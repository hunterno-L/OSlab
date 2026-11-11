// Native-MySQL equivalent of loader assertions; it does not claim Docker startup.
import {readFileSync,existsSync,writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../前端代码/osLab-frontend/package.json',import.meta.url));
const yaml=require('js-yaml');
const compose=yaml.load(readFileSync('deploy/docker-compose.yml','utf8'));
const script=readFileSync('deploy/mysql-init/10-load-oslab-db.sh','utf8');
assert(!script.includes('\r'),'Linux loader must use LF');
const shell=spawnSync('C:/Program Files/Git/bin/bash.exe',['-n','deploy/mysql-init/10-load-oslab-db.sh'],{encoding:'utf8'});assert.equal(shell.status,0);
const loads=[...script.matchAll(/^load_required_seed \/seed\/(\S+)/gm)].map(m=>m[1]);assert.equal(loads.length,17);
const mounts=compose.services.mysql.volumes.filter(x=>typeof x==='string');for(const file of loads){assert(existsSync(join('数据库文件',file)));assert(mounts.some(x=>x.endsWith(`:/seed/${file}:ro`)));}
const cfg=JSON.parse(readFileSync(join(process.env.TEMP,'oslab-terra-acceptance/verification-config.json'),'utf8'));assert.match(cfg.freshDatabase,/^oslab_terra_fresh_\d+$/);
const checks=[...script.matchAll(/expect_(scalar|min) \\\n\s+"((?:\\.|[^"\\])*)" \\\n\s+(\d+) "([^"\n]+)"/g)];assert(checks.length>=20,'not all loader assertions parsed');
for(const [,mode,query,want,label] of checks){const sql=query.replace(/\\`/g,'`');assert.match(sql,/^SELECT /);const r=spawnSync('mysql',['--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database',cfg.freshDatabase],{input:sql,encoding:'utf8'});assert.equal(r.status,0);const actual=r.stdout.trim();assert(mode==='scalar'?actual===want:Number(actual)>=Number(want),`${label}: expected ${mode} ${want}, actual ${actual}`);console.log(`PASS ${label}: ${actual}`);}
console.log(`PASS Shell syntax, YAML and ${loads.length} mounts; all ${checks.length} loader SQL assertions on fresh MySQL`);
console.log('Docker containers and Neo4j fresh import not executed.');
