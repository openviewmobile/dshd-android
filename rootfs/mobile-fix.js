(function () {
  if (window.__dshdPetFix) return;
  window.__dshdPetFix = true;
  function findPet() {
    var stack = [document];
    while (stack.length) {
      var root = stack.pop();
      var nodes = root.querySelectorAll('*');
      for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        if (node.shadowRoot) {
          var pet = node.shadowRoot.querySelector('.pet');
          if (pet) return pet;
          stack.push(node.shadowRoot);
        }
      }
    }
    return null;
  }
  function sync() {
    var pet = findPet();
    if (!pet) return;
    var keyboard = (window.visualViewport ? window.visualViewport.height : window.innerHeight) < 520;
    if (keyboard) {
      pet.style.visibility = 'hidden';
      return;
    }
    pet.style.visibility = '';
    var input = document.querySelector('[data-composer-input]');
    if (!input) return;
    var card = input.closest('[class*="composerStack"]') || input;
    var a = pet.getBoundingClientRect();
    var b = card.getBoundingClientRect();
    var overlap = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
    if (overlap) {
      var size = a.width || 156;
      pet.style.left = Math.max(8, window.innerWidth - size - 12) + 'px';
      pet.style.top = Math.max(64, window.innerHeight - size - 12) + 'px';
    }
  }
  window.addEventListener('resize', sync);
  if (window.visualViewport) window.visualViewport.addEventListener('resize', sync);
  setInterval(sync, 400);
  sync();
})();

/* Android has no desktop document opener. Hide the settings header's "open
 * configuration file" action by its localized label because the generated
 * CSS-module class name changes between frontend builds. */
(function () {
  function hideDesktopOpeners() {
    var labels = ['打开配置文件', 'Open config file', 'Open configuration file'];
    var buttons = document.querySelectorAll('button, [role="button"], a');
    for (var i = 0; i < buttons.length; i++) {
      var text = (buttons[i].textContent || '').trim();
      if (labels.indexOf(text) !== -1) {
        buttons[i].style.display = 'none';
      }
    }
  }
  hideDesktopOpeners();
  var observer = new MutationObserver(hideDesktopOpeners);
  observer.observe(document.documentElement, { childList: true, subtree: true });
})();
