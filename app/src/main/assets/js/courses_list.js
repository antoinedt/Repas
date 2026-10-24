var s = document.querySelector('select[id$="cboCours"]');
if (!s) return '[]';
return JSON.stringify(arr(s.options).map(function (o) {
  return { v: o.value, t: (o.text || '').trim(), sel: o.value === s.value };
}));
