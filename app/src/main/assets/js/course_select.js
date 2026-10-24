var s = document.querySelector('select[id$="cboCours"]');
if (!s) return 'noSel';
var e = document.querySelector('select[id$="cboEnseignant"]');
if (e) e.__cndOld = true;
var acc = document.querySelector('.pageonglet[data-id="ongletAccueil"]');
if (acc) acc.__cndOld = true;
s.value = __VAL__;
s.dispatchEvent(new Event('change', { bubbles: true }));
return 'ok';
