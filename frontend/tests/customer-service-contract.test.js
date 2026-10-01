const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const frontend = path.resolve(__dirname, '..');
const nginx = fs.readFileSync(path.join(frontend, 'conf', 'nginx.conf'), 'utf8');
const script = fs.readFileSync(path.join(frontend, 'html', 'hmall-portal', 'js', 'customer-service.js'), 'utf8');
const page = fs.readFileSync(path.join(frontend, 'html', 'hmall-portal', 'customer-service.html'), 'utf8');
const imageModulePath = path.join(frontend, 'html', 'hmall-portal', 'js', 'customer-service-images.js');

class FakeElement {
  constructor(tagName) {
    this.tagName = tagName.toUpperCase();
    this.className = '';
    this.children = [];
    this.listeners = {};
  }
  append(...children) { this.children.push(...children); }
  remove() {
    if (this.parent) this.parent.children = this.parent.children.filter(child => child !== this);
  }
  querySelector(selector) {
    const className = selector.startsWith('.') ? selector.slice(1) : '';
    return this.children.find(child => child.className === className) || null;
  }
  addEventListener(type, listener) { this.listeners[type] = listener; }
}

const fakeDocument = {
  createElement(tagName) {
    const element = new FakeElement(tagName);
    const append = element.append.bind(element);
    element.append = (...children) => {
      for (const child of children) child.parent = element;
      append(...children);
    };
    return element;
  }
};

test('browser keeps the public customer-service API and resume cursor', () => {
  assert.match(script, /fetch\(`\/api\/customer-service\$\{path\}`/);
  assert.match(script, /events\?after=\$\{session\.lastEventId \|\| 0\}/);
  assert.match(script, /result\.Authorization = userToken/);
});

test('nginx routes customer-service before the generic API proxy', () => {
  const customerLocation = nginx.indexOf('location /api/customer-service/');
  const genericLocation = nginx.indexOf('location /api {');
  assert.ok(customerLocation >= 0, 'missing customer-service location');
  assert.ok(customerLocation < genericLocation, 'customer-service route must precede /api');
  const block = nginx.slice(customerLocation, genericLocation);
  assert.match(block, /proxy_pass http:\/\/localhost:8080\/customer-service\//);
  assert.match(block, /proxy_set_header Authorization \$http_authorization/);
  assert.match(block, /proxy_buffering off/);
  assert.match(block, /proxy_read_timeout 300s/);
});

test('portal presents the Yuexiang brand and SVG logo', () => {
  const portal = path.join(frontend, 'html', 'hmall-portal');
  const visibleFiles = fs.readdirSync(portal).filter(name => name.endsWith('.html'))
    .map(name => path.join(portal, name))
    .concat([path.join(portal, 'js', 'top.js'), path.join(portal, 'js', 'foot.js')]);
  const visibleText = visibleFiles.map(file => fs.readFileSync(file, 'utf8')).join('\n');
  assert.doesNotMatch(visibleText, /黑马商城|黑马/);
  assert.match(visibleText, /悦享商城/);
  assert.ok(fs.existsSync(path.join(portal, 'img', 'yuexiang-logo.svg')));
  assert.doesNotMatch(visibleText, /img\/logo\.png/i);
});

test('page exposes guest, account and handoff entry points', () => {
  assert.match(page, /data-question="帮我看看有什么在售的商品"/);
  assert.match(page, /data-question="查询我的最近订单"/);
  assert.match(page, /id="handoff-button"/);
  assert.match(script, /ticket\.status !== 'QUEUED'/);
});

test('knowledge images render safely whether they arrive before or after the answer', () => {
  const { createRunImageRenderer } = require(imageModulePath);
  const renderer = createRunImageRenderer(fakeDocument);
  const firstRow = fakeDocument.createElement('div');

  renderer.receive('run-before', [
    'http://127.0.0.1:9000/manual/one.jpg',
    'javascript:alert(1)',
    'http://127.0.0.1:9000/manual/one.jpg',
    'https://example.com/manual/two.jpg'
  ]);
  renderer.bindRow('run-before', firstRow);

  const firstList = firstRow.querySelector('.knowledge-images');
  assert.equal(firstList.children.length, 2);
  assert.deepEqual(firstList.children.map(image => image.src), [
    'http://127.0.0.1:9000/manual/one.jpg',
    'https://example.com/manual/two.jpg'
  ]);

  const secondRow = fakeDocument.createElement('div');
  renderer.bindRow('run-after', secondRow);
  renderer.receive('run-after', ['https://example.com/manual/three.jpg']);
  assert.equal(secondRow.querySelector('.knowledge-images').children[0].src,
    'https://example.com/manual/three.jpg');
});

test('replayed image events replace the existing list instead of duplicating it', () => {
  const { createRunImageRenderer } = require(imageModulePath);
  const renderer = createRunImageRenderer(fakeDocument);
  const row = fakeDocument.createElement('div');
  renderer.bindRow('run-1', row);

  renderer.receive('run-1', ['https://example.com/old.jpg']);
  renderer.receive('run-1', ['https://example.com/new.jpg']);

  assert.equal(row.children.length, 1);
  assert.equal(row.querySelector('.knowledge-images').children[0].src,
    'https://example.com/new.jpg');
});
