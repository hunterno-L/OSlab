import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

// usage: node artifacts/experiment-judge/ch4/gen-body.mjs <id>
// 把 regions/<id>.c.inc（学生填写区）与共享 driver.c.inc 拼成 body-<id>.c。
// 驱动开关宏（CH4_USER_DRIVER / CH4_TRAP_DRIVER）由 mk.sh 前置的 config-*.c.inc 提供，
// 必须早于所有平台片段，故不在本文件里生成。
const id = process.argv[2];
if (!id) {
  console.error("usage: node artifacts/experiment-judge/ch4/gen-body.mjs <id>");
  process.exit(2);
}
const here = "artifacts/experiment-judge/ch4";
const region = readFileSync(join(here, "regions", `${id}.c.inc`), "utf8");
const driver = readFileSync(join(here, "driver.c.inc"), "utf8");
const out = region + "\n" + driver;
const path = join(here, `body-${id}.c`);
writeFileSync(path, out);
console.log(`wrote ${path} (${Buffer.byteLength(out, "utf8")} bytes)`);