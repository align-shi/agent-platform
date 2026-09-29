const text = process.argv.slice(2).join(' ');
const missing = [];
if (!text.trim()) {
  missing.push('正文');
}
if (!/(小时|分钟|今天|明天|工作日)/.test(text)) {
  missing.push('时限');
}
if (/(请您理解|这不是我们的问题)/.test(text)) {
  missing.push('推责用语');
}
console.log(JSON.stringify({ ok: missing.length === 0, missing }));
