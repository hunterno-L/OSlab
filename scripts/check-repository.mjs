#!/usr/bin/env node
// Check the staged repository boundary without printing any credential value.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
process.chdir(root);
const files = execFileSync('git', ['ls-files', '-z'], { encoding: 'utf8' }).split('\0').filter(Boolean);
const tracked = new Set(files);
const require = createRequire(path.resolve('前端代码/osLab-frontend/package.json'));
const yaml = require('js-yaml');
const errors = [];
const reject = message => errors.push(message);
const read = file => readFileSync(file, 'utf8');
const excluded = /(?:^|\/)(?:artifacts|design|node_modules|dist|target|tmp|\.qoder|\.vscode|\.idea)(?:\/|$)|(?:^|\/)\.env(?:\.(?!example$).*)?$|\.(?:log|pem|key)$/;
for (const file of files) {
  if (excluded.test(file)) reject(`Local or generated file tracked: ${file}`);
}

for (const required of [
  'README.md', '.env.example', 'deploy/.env.example', 'deploy/docker-compose.yml',
  'docs/environment.md', 'docs/testing.md', '数据库文件/README.md',
  '前端代码/osLab-frontend/package-lock.json', '后端代码/osLab-backend/pom.xml',
  '代码沙箱/server.py', '代码沙箱/runner.py',
]) {
  if (!tracked.has(required)) reject(`Required file is not tracked: ${required}`);
}

const env = new Map();
for (const line of read('deploy/.env.example').split(/\r?\n/)) {
  const match = /^([A-Z][A-Z0-9_]*)=(.*)$/.exec(line);
  if (!match) continue;
  if (env.has(match[1])) reject(`Duplicate template variable: ${match[1]}`);
  env.set(match[1], match[2]);
}
const composeText = read('deploy/docker-compose.yml');
const composeVariables = new Set([...composeText.matchAll(/\$\{([A-Z_][A-Z0-9_]*)(?::[-?][^}]*)?\}/g)].map(match => match[1]));
for (const key of composeVariables) if (!env.has(key)) reject(`Compose variable missing from template: ${key}`);
for (const key of env.keys()) if (!composeVariables.has(key)) reject(`Template variable not wired into Compose: ${key}`);
const compose = yaml.load(composeText);
for (const [name, service] of Object.entries(compose.services)) {
  const hostPaths = (service.volumes || []).filter(volume => typeof volume === 'string' && volume.startsWith('.')).map(volume => volume.split(':')[0]);
  if (service.build) hostPaths.push(path.join(service.build.context, service.build.dockerfile || 'Dockerfile'));
  for (const relative of hostPaths) {
    const file = path.resolve('deploy', relative);
    const repositoryPath = path.relative(root, file).split(path.sep).join('/');
    if (!tracked.has(repositoryPath)) reject(`Service ${name} references an untracked input: ${repositoryPath}`);
  }
}
for (const match of read('deploy/mysql-init/10-load-oslab-db.sh').matchAll(/^load_required_seed\s+\/seed\/(\S+)/gm)) {
  const file = `数据库文件/${match[1]}`;
  if (!tracked.has(file)) reject(`Database loader input missing: ${file}`);
}

const documentFiles = files.filter(file => file.endsWith('.md'));
for (const file of documentFiles) {
  // Code examples can contain pseudo-links such as M[$sp]($v0).
  const markdown = read(file).replace(/```[\s\S]*?```/g, '').replace(/`[^`\n]*`/g, '');
  for (const match of markdown.matchAll(/\[[^\]\n]*\]\(([^)\n]+)\)/g)) {
    const target = match[1].split(' ')[0];
    if (/^(?:https?:|mailto:|#|app:|codex:|data:|\$)/.test(target)) continue;
    const resolved = path.resolve(path.dirname(file), decodeURIComponent(target.split('#')[0]));
    const repositoryPath = path.relative(root, resolved).split(path.sep).join('/');
    const directoryExistsInTree = files.some(candidate => candidate.startsWith(`${repositoryPath}/`));
    if (!tracked.has(repositoryPath) && !directoryExistsInTree) reject(`Broken repository link: ${file} -> ${target}`);
  }
}

const secretPatterns = [
  ['provider key', /\bsk-[A-Za-z0-9_-]{24,}/],
  ['GitHub token', /\b(?:ghp_|github_pat_|gho_)[A-Za-z0-9_]{25,}/],
  ['AWS key', /\bAKIA[0-9A-Z]{16}\b/],
  ['private key', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/],
];
for (const file of files) {
  if (!existsSync(file) || statSync(file).size > 10_000_000 || !/\.(?:md|ps1|js|ts|mjs|vue|json|yml|yaml|py|xml|html|sql|example)$/.test(file)) continue;
  const text = read(file);
  for (const [label, pattern] of secretPatterns) if (pattern.test(text)) reject(`Possible ${label}; inspect privately: ${file}`);
}

const readme = read('README.md');
for (const name of ['江子涵', '刘澈', '安家成']) assert(readme.includes(name), `Team member missing: ${name}`);
if (errors.length) {
  console.error(errors.join('\n'));
  process.exit(1);
}
console.log(`Repository checks passed: ${files.length} files, ${documentFiles.length} Markdown documents, ${composeVariables.size} Compose variables, ${Object.keys(compose.services).length} services.`);
console.log('Secret pattern scanning is a check of this tree, not a guarantee or a rewrite of Git history.');
