/* eslint-disable @typescript-eslint/no-require-imports -- Cargador de TypeScript para node:test. */
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
module.exports = function harness(
  fetcher = async () => {
    throw new Error("HTTP no esperado");
  },
  globals = {},
) {
  const root = path.resolve(__dirname, "../../..");
  const cache = {};
  function load(relative) {
    const base = path.isAbsolute(relative)
      ? relative
      : path.join(root, relative);
    if (base.endsWith(".css"))
      return { default: new Proxy({}, { get: (_, name) => String(name) }) };
    const file = [
      base,
      `${base}.ts`,
      `${base}.tsx`,
      path.join(base, "index.ts"),
    ].find((f) => fs.existsSync(f) && fs.statSync(f).isFile());
    if (!file) throw new Error(`Módulo no encontrado: ${relative}`);
    if (cache[file]) return cache[file].exports;
    const entry = { exports: {} };
    cache[file] = entry;
    const code = ts.transpileModule(fs.readFileSync(file, "utf8"), {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
        jsx: ts.JsxEmit.ReactJSX,
      },
    }).outputText;
    vm.runInNewContext(code, {
      exports: entry.exports,
      module: entry,
      require: (id) =>
        id.startsWith("@/")
          ? load(id.slice(2))
          : id.startsWith(".")
            ? load(path.join(path.dirname(file), id))
            : require(id),
      process: { env: { NEXT_PUBLIC_API_BASE_URL: "http://api.test" } },
      fetch: fetcher,
      Headers,
      AbortController,
      TextEncoder,
      setTimeout,
      clearTimeout,
      ...globals,
    });
    return entry.exports;
  }
  return load;
};
