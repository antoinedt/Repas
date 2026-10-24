var U = __USER__, P = __PASS__;
var pws = arr(document.querySelectorAll('input[type=password]'));
var pw = pws.filter(vis)[0] || pws[0];
if (!pw) return 'nopw';
var scope = pw.form || document;
var sel = 'input[type=text],input[type=email],input:not([type])';
var us = arr(scope.querySelectorAll(sel)).filter(vis);
if (!us.length) us = arr(document.querySelectorAll(sel)).filter(vis);
var u = us[0];
if (!u) return 'nouser';
var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
function setv(el, v) {
  el.focus();
  setter.call(el, v);
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
  el.blur();
}
setv(u, U);
setv(pw, P);
var cands = arr(scope.querySelectorAll('button,input[type=submit],input[type=button],input[type=image],a')).filter(vis);
var btn = cands.filter(function (b) {
  var s = ((b.innerText || '') + ' ' + (b.value || '') + ' ' + (b.id || '') + ' ' + (b.title || '')).toLowerCase();
  return /connex|connect|login|entrer|ouvrir une session|soumettre|valider/.test(s) && !/oubli/.test(s);
})[0];
if (!btn) btn = arr(scope.querySelectorAll('input[type=submit],button[type=submit]')).filter(vis)[0];
if (btn) { btn.click(); return 'btn:' + (btn.id || btn.value || (btn.innerText || '').trim()); }
if (pw.form) {
  if (pw.form.requestSubmit) pw.form.requestSubmit(); else pw.form.submit();
  return 'form';
}
['keydown', 'keypress', 'keyup'].forEach(function (k) {
  pw.dispatchEvent(new KeyboardEvent(k, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
});
return 'enter';
