(function () {
  'use strict';

  // Compute base URL for fetch() so page fragments load correctly
  // regardless of where the site is hosted (GitHub Pages subpath, custom domain, etc.)
  // We derive it from the script's own absolute URL.
  const scriptSrc = (document.currentScript || document.querySelector('script[src*="app.js"]')).src;
  const baseURL = scriptSrc.substring(0, scriptSrc.lastIndexOf('/') + 1);

  const pageCache = new Map();
  const landingView = document.getElementById('landingView');
  const docsView = document.getElementById('docsView');
  const sidebarLinks = document.querySelectorAll('.sidebar-link');
  const breadcrumb = document.getElementById('breadcrumb');
  const pageContent = document.getElementById('pageContent');
  const sidebar = document.getElementById('sidebar');
  const overlay = document.getElementById('sidebarOverlay');
  const toggle = document.getElementById('mobileToggle');

  function showLanding() {
    document.body.className = 'landing';
    landingView.style.display = '';
    docsView.style.display = 'none';
    window.scrollTo(0, 0);
  }

  function showDocs(pageId) {
    document.body.className = 'docs';
    landingView.style.display = 'none';
    docsView.style.display = 'flex';
    loadPage(pageId);
  }

  async function loadPage(pageId) {
    // Update sidebar active state
    sidebarLinks.forEach(l => l.classList.remove('active'));
    const activeLink = document.querySelector(`[data-page="${pageId}"]`);
    if (activeLink) {
      activeLink.classList.add('active');
      breadcrumb.textContent = activeLink.textContent;
    }

    // Close mobile sidebar
    sidebar.classList.remove('open');
    overlay.classList.remove('open');

    // Check cache
    if (pageCache.has(pageId)) {
      pageContent.innerHTML = pageCache.get(pageId);
      window.scrollTo(0, 0);
      return;
    }

    // Show loading state
    pageContent.innerHTML = '<div style="padding:4rem 0;color:var(--text-dim);text-align:center;">Loading...</div>';

    try {
      const resp = await fetch(`${baseURL}pages/${pageId}.html`);
      if (!resp.ok) throw new Error('Page not found');
      const html = await resp.text();
      pageCache.set(pageId, html);
      pageContent.innerHTML = html;
    } catch (e) {
      pageContent.innerHTML = '<div style="padding:4rem 0;color:var(--red);text-align:center;">Page not found.</div>';
    }

    window.scrollTo(0, 0);
  }

  function route() {
    const hash = window.location.hash.slice(1);
    if (!hash || hash === '/') {
      showLanding();
    } else {
      showDocs(hash);
    }
  }

  // Sidebar navigation
  sidebarLinks.forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const page = link.dataset.page;
      history.pushState(null, '', '#' + page);
      showDocs(page);
    });
  });

  // Landing "Get Started" / "Read the Docs" links
  document.querySelectorAll('[data-nav]').forEach(el => {
    el.addEventListener('click', (e) => {
      e.preventDefault();
      const target = el.dataset.nav;
      history.pushState(null, '', '#' + target);
      showDocs(target);
    });
  });

  // Logo click -> landing
  document.querySelectorAll('.logo-link').forEach(el => {
    el.addEventListener('click', (e) => {
      e.preventDefault();
      history.pushState(null, '', window.location.pathname);
      showLanding();
    });
  });

  // Mobile sidebar toggle
  if (toggle) {
    toggle.addEventListener('click', () => {
      sidebar.classList.toggle('open');
      overlay.classList.toggle('open');
    });
  }

  if (overlay) {
    overlay.addEventListener('click', () => {
      sidebar.classList.remove('open');
      overlay.classList.remove('open');
    });
  }

  // Handle back/forward
  window.addEventListener('popstate', route);

  // Initial route
  route();
})();
