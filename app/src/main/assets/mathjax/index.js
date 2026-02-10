const adaptor = MathJax._.adaptors.liteAdaptor.liteAdaptor();
const html = MathJax._.handlers.html;
const fontData = MathJax.config.svg.fontData;
const tex = new MathJax._.input.tex_ts.TeX({
    packages: globalThis.texPackages || ['base'],
    formatError: (jax, err) => {
        throw err;
    },
});
const svg = new MathJax._.output.svg_ts.SVG({
    linebreaks: {inline: false},
    fontData: fontData,
});

const emptyDocString = '<html lang=""><head><meta charset="utf-8"/></head><body></body></html>';
const rootDocument = adaptor.parse(emptyDocString, 'text/html');

MathJax._.mathjax.mathjax.asyncLoad = async (name) => {
    console.log('Loading module: ' + name);
    eval(await loadExternal(name));
    console.log('Module loaded: ' + name);
};
MathJax._.mathjax.mathjax.asyncIsSynchronous = false;

function doRender(document, expression, options) {
    const node = document.convert(expression, options);
    return {
        html: adaptor.innerHTML(node),
        attributes: node.children.reduce((acc, child) => {
            if (child.kind === 'svg') {
                acc.push(child.attributes);
            }
            return acc;
        }, []),
    };
}

async function renderToSvg(expression, options) {
    const document = new html.HTMLDocument.HTMLDocument(rootDocument, adaptor, {
        InputJax: tex,
        OutputJax: svg,
    });

    while (true) {
        try {
            const result = doRender(document, expression, options);
            console.log('Rendering complete.');
            return result;
        } catch (e) {
            if (e.retry) {
                console.log('Waiting for async load...');
                await e.retry;
                console.log('Retrying render...');
            } else {
                throw e;
            }
        }
    }
}
