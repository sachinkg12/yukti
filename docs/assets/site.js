(() => {
  const tabsRoot = document.querySelector('[data-tabs]');
  if (tabsRoot) {
    const tabs = [...tabsRoot.querySelectorAll('[role="tab"]')];
    const panels = [...tabsRoot.querySelectorAll('[role="tabpanel"]')];

    const activateTab = (tab) => {
      tabs.forEach((candidate) => {
        const selected = candidate === tab;
        candidate.setAttribute('aria-selected', String(selected));
        candidate.tabIndex = selected ? 0 : -1;
      });
      panels.forEach((panel) => {
        panel.hidden = panel.id !== tab.getAttribute('aria-controls');
      });
    };

    tabs.forEach((tab, index) => {
      tab.addEventListener('click', () => activateTab(tab));
      tab.addEventListener('keydown', (event) => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        let next = index;
        if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
        if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
        if (event.key === 'Home') next = 0;
        if (event.key === 'End') next = tabs.length - 1;
        activateTab(tabs[next]);
        tabs[next].focus();
      });
    });
  }

  const menuButton = document.querySelector('.menu-button');
  const sidebar = document.querySelector('.sidebar');
  if (menuButton && sidebar) {
    const closeMenu = () => {
      sidebar.classList.remove('open');
      menuButton.setAttribute('aria-expanded', 'false');
      menuButton.setAttribute('aria-label', 'Open navigation');
    };
    menuButton.addEventListener('click', () => {
      const open = sidebar.classList.toggle('open');
      menuButton.setAttribute('aria-expanded', String(open));
      menuButton.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
    });
    sidebar.querySelectorAll('a').forEach((link) => link.addEventListener('click', closeMenu));
  }

  const navLinks = [...document.querySelectorAll('.side-nav a')];
  const sections = [...document.querySelectorAll('main > section[id]')];
  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver((entries) => {
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
      if (!visible) return;
      navLinks.forEach((link) => {
        link.classList.toggle('active', link.getAttribute('href') === `#${visible.target.id}`);
      });
    }, { rootMargin: '-15% 0px -70% 0px', threshold: [0, .15, .4] });
    sections.forEach((section) => observer.observe(section));
  }

  document.querySelectorAll('[data-copy]').forEach((button) => {
    button.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(button.dataset.copy);
        const previous = button.textContent;
        button.textContent = 'Copied';
        button.classList.add('copied');
        window.setTimeout(() => {
          button.textContent = previous;
          button.classList.remove('copied');
        }, 1500);
      } catch {
        button.textContent = 'Select text';
      }
    });
  });

  const year = document.getElementById('year');
  if (year) year.textContent = String(new Date().getFullYear());
})();
