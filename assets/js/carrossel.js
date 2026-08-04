(function () {
  var trilho = document.getElementById('trilho');
  if (!trilho) return;

  var slides = trilho.children;
  var total = slides.length;
  var pontos = document.getElementById('pontos');
  var atual = 0;
  var timer;

  for (var i = 0; i < total; i++) {
    var b = document.createElement('button');
    b.setAttribute('aria-label', 'Banner ' + (i + 1));
    b.dataset.i = i;
    pontos.appendChild(b);
  }

  function mostrar(i) {
    atual = (i + total) % total;
    trilho.style.transform = 'translateX(' + (-atual * 100) + '%)';
    for (var k = 0; k < pontos.children.length; k++) {
      pontos.children[k].classList.toggle('ativo', k === atual);
    }
  }

  function reiniciar() {
    clearInterval(timer);
    timer = setInterval(function () { mostrar(atual + 1); }, 5000);
  }

  pontos.addEventListener('click', function (e) {
    if (e.target.dataset.i === undefined) return;
    mostrar(+e.target.dataset.i);
    reiniciar();
  });
  document.getElementById('anterior').addEventListener('click', function () {
    mostrar(atual - 1); reiniciar();
  });
  document.getElementById('proximo').addEventListener('click', function () {
    mostrar(atual + 1); reiniciar();
  });

  // arrastar no celular
  var x0 = null;
  var carrossel = document.getElementById('carrossel');
  carrossel.addEventListener('touchstart', function (e) { x0 = e.touches[0].clientX; }, { passive: true });
  carrossel.addEventListener('touchend', function (e) {
    if (x0 === null) return;
    var d = e.changedTouches[0].clientX - x0;
    if (Math.abs(d) > 40) { mostrar(atual + (d < 0 ? 1 : -1)); reiniciar(); }
    x0 = null;
  });

  mostrar(0);
  reiniciar();
})();
