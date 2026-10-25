export interface PackageFile {
  name: string;
  webkitRelativePath?: string;
}
const clean = (path: string) => path.replace(/\\/g, "/").replace(/^\.\//, "");
/** Match references against package-local then ancestor roots; never flatten ambiguous filenames. */
export function selectPackageAttachments<T extends PackageFile>(
  mainPath: string,
  pkg: Record<string, unknown>,
  files: T[]
): { file: T; path: string }[] {
  const experiment = String(pkg.schemaVersion || "").startsWith(
    "oslab-experiment-maintenance/"
  );
  const references = new Set<string>();
  const visit = (value: unknown) => {
    if (typeof value === "string") {
      const candidates = /^(?:assets|experiment-files)\//.test(value)
        ? [value]
        : Array.from(
            value.matchAll(/(?:assets|experiment-files)\/[^\s)"<>]+/g),
            (match) => match[0]
          );
      for (const candidate of candidates) {
        const path = clean(candidate);
        if (!path.split("/").includes("..")) references.add(path);
      }
    } else if (Array.isArray(value)) value.forEach(visit);
    else if (value && typeof value === "object")
      Object.values(value).forEach(visit);
  };
  visit(pkg);
  const directory = clean(mainPath).split("/").slice(0, -1);
  const roots: string[] = [];
  for (let i = directory.length; i >= 0; i--)
    roots.push(directory.slice(0, i).join("/"));
  const result: { file: T; path: string }[] = [];
  for (const reference of references) {
    if (
      experiment
        ? !reference.startsWith("experiment-files/")
        : !reference.startsWith("assets/") ||
          !/\.(png|jpe?g|webp|gif)$/i.test(reference)
    )
      continue;
    let match: T | undefined;
    for (const root of roots) {
      const expected = (root ? root + "/" : "") + reference;
      match = files.find(
        (file) => clean(file.webkitRelativePath || file.name) === expected
      );
      if (match) break;
    }
    // A separately selected image has no directory metadata. Only an unambiguous basename may resolve it.
    if (!match) {
      const matches = files.filter(
        (file) =>
          !file.webkitRelativePath && file.name === reference.split("/").pop()
      );
      if (matches.length === 1) match = matches[0];
    }
    if (!match) throw new Error(`缺少资源包引用的附件：${reference}`);
    result.push({ file: match, path: reference });
  }
  return result;
}
