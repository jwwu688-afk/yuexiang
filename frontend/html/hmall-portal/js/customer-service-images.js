((root, factory) => {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.CustomerServiceImages = api;
})(typeof globalThis === 'object' ? globalThis : this, () => {
  function normalizeImageUrls(values) {
    const result = [];
    for (const value of Array.isArray(values) ? values : []) {
      if (typeof value !== 'string') continue;
      try {
        const parsed = new URL(value);
        if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') continue;
      } catch (_) { continue; }
      if (!result.includes(value)) result.push(value);
      if (result.length === 5) break;
    }
    return result;
  }

  function renderKnowledgeImages(row, values, documentRef) {
    const previous = row.querySelector('.knowledge-images');
    if (previous) previous.remove();
    const images = normalizeImageUrls(values);
    if (!images.length) return;
    const list = documentRef.createElement('div');
    list.className = 'knowledge-images';
    for (const url of images) {
      const image = documentRef.createElement('img');
      image.src = url;
      image.alt = '产品说明书图片';
      image.loading = 'lazy';
      image.addEventListener('error', () => image.remove(), { once: true });
      list.append(image);
    }
    row.append(list);
  }

  function createRunImageRenderer(documentRef) {
    const imageByRun = new Map();
    const rowByRun = new Map();
    const render = runId => {
      const row = rowByRun.get(runId);
      if (row) renderKnowledgeImages(row, imageByRun.get(runId) || [], documentRef);
    };
    return {
      clear() {
        imageByRun.clear();
        rowByRun.clear();
      },
      bindRow(runId, row) {
        if (!runId || !row) return;
        rowByRun.set(runId, row);
        render(runId);
      },
      receive(runId, images) {
        if (!runId) return;
        imageByRun.set(runId, normalizeImageUrls(images));
        render(runId);
      }
    };
  }

  return { createRunImageRenderer, normalizeImageUrls, renderKnowledgeImages };
});
