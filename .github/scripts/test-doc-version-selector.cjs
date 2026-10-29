const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "doc-version-selector.js"), "utf8");
const versions = ["0.0.15", "0.0.14"];

function element() {
    return {
        children: [],
        appendChild(child) { this.children.push(child); },
        replaceChildren(child) { this.children = [child]; },
        setAttribute(name, value) { this[name] = value; },
        addEventListener(event, listener) { this[event] = listener; }
    };
}

for (const base of [
    "https://contentauth.github.io/c2pa-android/",
    "https://docs.example.test/nested/sdk/"
]) {
    for (const prefix of ["", ...versions.map(tag => "versions/" + tag)]) {
        test("Dokka version selector: " + base + (prefix || "default"), function () {
            const tag = prefix ? prefix.split("/").pop() : versions[0];
            const container = element();
            let destination;
            vm.runInNewContext(source, {
                URL,
                document: {
                    currentScript: {
                        src: new URL((prefix ? prefix + "/" : "") + "doc-version-selector.js", base).href,
                        dataset: { versions: JSON.stringify(versions), version: tag, prefix }
                    },
                    getElementById(id) {
                        assert.equal(id, "library-version");
                        return container;
                    },
                    createElement: element
                },
                window: { location: { assign(url) { destination = url; } } }
            });
            assert.equal(container.children.length, 1);
            const label = container.children[0];
            assert.equal(label.id, "c2pa-doc-version-selector");
            const select = label.children[0];
            assert.equal(select["aria-label"], "API documentation version");
            assert.equal(select.children.length, versions.length);
            for (const [index, option] of select.children.entries()) {
                assert.equal(option.value, base + "versions/" + versions[index] + "/");
                assert.equal(option.selected, versions[index] === tag);
                assert.equal(option.textContent, versions[index]);
            }
            select.value = select.children[1].value;
            select.change();
            assert.equal(destination, base + "versions/0.0.14/");
        });
    }
}

test("A missing Dokka version slot fails explicitly", function () {
    assert.throws(() => vm.runInNewContext(source, {
        URL,
        document: {
            currentScript: {
                src: "https://contentauth.github.io/c2pa-android/doc-version-selector.js",
                dataset: { versions: JSON.stringify(versions), version: versions[0], prefix: "" }
            },
            getElementById() { return null; }
        }
    }), /Dokka did not provide a library-version header slot/);
});
