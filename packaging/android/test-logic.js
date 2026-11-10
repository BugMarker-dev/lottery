/**
 * 手机端核心逻辑冒烟测试（node 直接跑，无需浏览器）。
 * 从 mobile/index.html 抽出 <script>，用 DOM 桩执行，再验证算法不变量。
 */
const fs = require('fs');
const path = require('path');

const html = fs.readFileSync(path.join(__dirname, '..', '..', 'mobile', 'index.html'), 'utf8');
const m = html.match(/<script>\n([\s\S]*?)<\/script>/);
if (!m) { console.error('未找到脚本'); process.exit(1); }
const src = m[1];

// ---- DOM / 浏览器桩 ----
const listeners = {};
const fakeEl = () => ({
  textContent: '', innerHTML: '', value: '5', classList: {
    add() {}, remove() {}, toggle() {}
  },
  addEventListener() {}, dataset: {}
});
const els = {};
global.document = {
  getElementById: (id) => (els[id] = els[id] || fakeEl()),
  querySelectorAll: () => [],
  createElement: () => fakeEl()
};
global.window = global;
global.addEventListener = () => {};                 // window.addEventListener 桩
global.document.addEventListener = () => {};        // document.addEventListener 桩
global.document.visibilityState = 'visible';
global.localStorage = {
  _d: {}, getItem(k) { return this._d[k] || null; },
  setItem(k, v) { this._d[k] = v; }
};
// 阻止启动时的真实网络请求
global.fetch = () => Promise.reject(new Error('no network in test'));
global.setTimeout = (fn) => 0;   // 状态轮询等全部短路
const realSetTimeout = setTimeout;

let failures = 0;
const check = (name, cond) => {
  console.log((cond ? 'PASS' : 'FAIL') + '  ' + name);
  if (!cond) failures++;
};

eval(src);
const dlt = global.__dlt;

// ---- 1. 随机：1000 注全部合法且无重复 ----
let ok = true;
for (let i = 0; i < 1000; i++) {
  const t = dlt.randomTicket();
  const f = new Set(t.front), b = new Set(t.back);
  if (t.front.length !== 5 || t.back.length !== 2) ok = false;
  if (f.size !== 5 || b.size !== 2) ok = false;
  if (t.front.some(n => n < 1 || n > 35) || t.back.some(n => n < 1 || n > 12)) ok = false;
}
check('随机 1000 注：个数/范围/去重全部合法', ok);

// ---- 2. 构造假历史（期号乱序传入，验证内部排序与统计） ----
// 3 期：25003 / 25001 / 25002，故意乱序
const hist = [
  { issue: '25001', date: '2025-01-03', front: [1, 2, 3, 4, 5], back: [1, 2] },
  { issue: '25003', date: '2025-01-05', front: [5, 6, 7, 8, 9], back: [2, 3] },
  { issue: '25002', date: '2025-01-04', front: [9, 10, 11, 12, 13], back: [3, 4] }
].sort(() => Math.random() - 0.5);   // 随机打乱

const w = dlt.buildWeights(hist);
// 前区权重和应为 1（归一化 + 平滑）
const sumF = w.front.slice(1).reduce((a, b) => a + b, 0);
const sumB = w.back.slice(1).reduce((a, b) => a + b, 0);
check('前区权重和 = 1（实际 ' + sumF.toFixed(6) + '）', Math.abs(sumF - 1) < 1e-9);
check('后区权重和 = 1（实际 ' + sumB.toFixed(6) + '）', Math.abs(sumB - 1) < 1e-9);
check('无零权重（抽样不会死循环）', w.front.slice(1).every(x => x > 0) && w.back.slice(1).every(x => x > 0));

// 近 30 期窗口：只有 3 期时 recentUsed = 3
check('recentUsed = 3（不足 30 取全部）', w.recentUsed === 3);

// ---- 3. 加权抽样：1000 注全部合法 ----
ok = true;
const uniqCover = new Set();
for (let i = 0; i < 1000; i++) {
  const t = dlt.predictTickets(hist, 1)[0];
  const f = new Set(t.front), b = new Set(t.back);
  if (t.front.length !== 5 || t.back.length !== 2 || f.size !== 5 || b.size !== 2) ok = false;
  if (t.front.some(n => n < 1 || n > 35) || t.back.some(n => n < 1 || n > 12)) ok = false;
  t.front.forEach(n => uniqCover.add(n));
}
check('预测 1000 注：个数/范围/去重全部合法', ok);
check('预测抽样覆盖面（1000 注覆盖 >25 个前区号，实际 ' + uniqCover.size + '）', uniqCover.size > 25);

// ---- 4. 热号确实更热：1 号每期都开，其他号随机，1 的权重应严格最大 ----
const heavy = [];
for (let i = 0; i < 100; i++) {
  const pool = [];
  for (let n = 2; n <= 35; n++) pool.push(n);
  const rest = [];
  for (let k = 0; k < 4; k++) rest.push(pool.splice(Math.floor(Math.random() * pool.length), 1)[0]);
  heavy.push({ issue: String(26000 + i), date: '2026-01-01', front: [1].concat(rest.sort((a, b) => a - b)), back: [1, 2] });
}
const wh = dlt.buildWeights(heavy);
const maxOther = Math.max(...wh.front.slice(2));
check('热号 1 权重严格最大（' + wh.front[1].toFixed(4) + ' > 次大 ' + maxOther.toFixed(4) + ')', wh.front[1] > maxOther);

// ---- 5. parseRecords：脏数据过滤 + 排序 ----
const parsed = dlt.parseRecords({
  value: { list: [
    { lotteryDrawNum: '25003', lotteryDrawTime: '2025-01-05', lotteryDrawResult: '03 13 20 25 34 09 12' },
    { lotteryDrawNum: '25001', lotteryDrawTime: '2025-01-03', lotteryDrawResult: '01 02 03 04 05 06 07' },
    { lotteryDrawNum: '25002', lotteryDrawTime: '2025-01-04', lotteryDrawResult: 'bad data' },       // 应被跳过
    { lotteryDrawNum: '25004', lotteryDrawTime: '2025-01-06', lotteryDrawResult: '01 02 03 04 05' }  // 个数不足，跳过
  ] }
});
check('parseRecords：脏数据被过滤（剩 2 条）', parsed.length === 2);
check('parseRecords：期号降序（' + parsed.map(r => r.issue).join(',') + '）',
  parsed[0].issue === '25003' && parsed[1].issue === '25001');
check('parseRecords：号码切分正确', parsed[0].front.length === 5 && parsed[0].back.join() === '9,12');


// ---- 6. fetchHistory：接口失败时自动切内置离线数据 ----
(async () => {
  // 页面 IIFE 启动时已自动执行过 fetchHistory(false)，fetch 桩必失败两次后走 FALLBACK
  for (let i = 0; i < 5; i++) await Promise.resolve();
  await new Promise(r => require('timers').setTimeout(r, 5));
  const st = global.__dlt.getState();
  const hist = st.history;
  check('接口失败 -> 内置兜底数据生效（100 期）', Array.isArray(hist) && hist.length === 100);
  check('兜底数据最新一期 26128（' + (hist ? hist[0].issue : '无') + '）', hist && hist[0].issue === '26128');
  console.log(failures === 0 ? '\n全部通过' : '\n有 ' + failures + ' 项失败');
  process.exit(failures === 0 ? 0 : 1);
})();

