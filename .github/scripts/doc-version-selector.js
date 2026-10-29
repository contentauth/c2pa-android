(function () {
    "use strict";

    const script = document.currentScript;
    const versions = JSON.parse(script.dataset.versions);
    const assets = new URL(".", script.src);
    const root = new URL(script.dataset.prefix ? "../../" : "./", assets);
    const container = document.getElementById("library-version");
    if (!container) {
        throw new Error("Dokka did not provide a library-version header slot.");
    }
    const label = document.createElement("label");
    label.id = "c2pa-doc-version-selector";
    label.textContent = "Version: ";
    const select = document.createElement("select");
    select.setAttribute("aria-label", "API documentation version");
    for (const tag of versions) {
        const option = document.createElement("option");
        option.value = new URL("versions/" + tag + "/", root).href;
        option.textContent = tag;
        option.selected = tag === script.dataset.version;
        select.appendChild(option);
    }
    select.addEventListener("change", function () {
        window.location.assign(select.value);
    });
    label.appendChild(select);
    container.replaceChildren(label);
})();
