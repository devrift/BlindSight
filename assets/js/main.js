/* ==========================================================================
   BlindSight Documentation — Interactive Glassmorphic Engine
   Zero external dependencies. Pure vanilla ES6+.
   ========================================================================== */
(function () {
  'use strict';

  /* ------------------------------------------------------------------ Theme State */
  const savedTheme = (() => {
    try { return localStorage.getItem('blindsight-theme'); } catch (e) { return null; }
  })();
  const initialTheme = savedTheme || 'dark';
  document.documentElement.setAttribute('data-theme', initialTheme);

  window.setTheme = function (t) {
    document.documentElement.setAttribute('data-theme', t);
    try { localStorage.setItem('blindsight-theme', t); } catch (e) { }
    document.querySelectorAll('[data-theme-toggle]').forEach((btn) => {
      btn.setAttribute('aria-label', t === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
    });
  };

  document.addEventListener('DOMContentLoaded', function () {
    /* Theme Toggle */
    document.querySelectorAll('[data-theme-toggle]').forEach((btn) => {
      btn.addEventListener('click', function () {
        const cur = document.documentElement.getAttribute('data-theme');
        window.setTheme(cur === 'dark' ? 'light' : 'dark');
      });
    });

    /* Mobile Hamburger Nav */
    const burger = document.querySelector('[data-nav-burger]');
    const navLinks = document.querySelector('.nav-links');
    if (burger && navLinks) {
      burger.addEventListener('click', function () {
        navLinks.classList.toggle('open');
      });
      navLinks.addEventListener('click', function (e) {
        if (e.target.tagName === 'A') navLinks.classList.remove('open');
      });
    }

    /* Scroll Progress Indicator & Back to Top */
    const progressBar = document.getElementById('scroll-progress');
    const backToTop = document.getElementById('back-to-top');

    window.addEventListener('scroll', function () {
      const winScroll = document.documentElement.scrollTop || document.body.scrollTop;
      const height = document.documentElement.scrollHeight - document.documentElement.clientHeight;
      const scrolled = height > 0 ? (winScroll / height) * 100 : 0;
      if (progressBar) progressBar.style.width = scrolled + '%';

      if (backToTop) {
        if (winScroll > 420) {
          backToTop.classList.add('visible');
        } else {
          backToTop.classList.remove('visible');
        }
      }
    }, { passive: true });

    if (backToTop) {
      backToTop.addEventListener('click', function () {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    }

    /* Dynamic Glass Spotlight Hover Effect */
    document.querySelectorAll('.glass-card').forEach((card) => {
      card.addEventListener('mousemove', function (e) {
        const rect = card.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        card.style.setProperty('--mouse-x', `${x}px`);
        card.style.setProperty('--mouse-y', `${y}px`);
      });
    });

    /* Interactive Tab Switchers */
    document.querySelectorAll('[data-tabs]').forEach((wrap) => {
      const btns = wrap.querySelectorAll('.tab-btn');
      const panels = wrap.querySelectorAll('.tab-panel');

      btns.forEach((btn) => {
        btn.addEventListener('click', function () {
          const target = btn.getAttribute('data-tab');
          btns.forEach((b) => b.classList.toggle('active', b === btn));
          panels.forEach((p) => p.classList.toggle('active', p.id === target));
        });
      });
    });

    /* Interactive Accordion Cards */
    document.querySelectorAll('.accordion-header').forEach((head) => {
      head.addEventListener('click', function () {
        const item = head.closest('.accordion-item');
        if (item) {
          item.classList.toggle('open');
        }
      });
    });

    /* Enhance Code Blocks: Syntax Highlighting & Copy */
    document.querySelectorAll('pre[data-lang]').forEach((pre) => {
      const code = pre.textContent;
      const lang = pre.getAttribute('data-lang');
      const name = pre.getAttribute('data-name') || `${lang}`;

      // Wrap codeblock if not wrapped
      if (!pre.closest('.code-block')) {
        const wrap = document.createElement('div');
        wrap.className = 'code-block';
        pre.parentNode.insertBefore(wrap, pre);

        const head = document.createElement('div');
        head.className = 'cb-head';
        head.innerHTML = `
          <div class="cb-dots"><i></i><i></i><i></i></div>
          <span class="cb-title">${name}</span>
          <button class="cb-copy" aria-label="Copy code">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
            <span>copy</span>
          </button>
        `;
        wrap.appendChild(head);

        if (window.BlindSightHL) {
          pre.innerHTML = window.BlindSightHL.highlight(code, lang);
        }
        wrap.appendChild(pre);

        const copyBtn = head.querySelector('.cb-copy');
        copyBtn.addEventListener('click', function () {
          navigator.clipboard.writeText(code).then(() => {
            const span = copyBtn.querySelector('span');
            const orig = span.textContent;
            span.textContent = 'copied ✓';
            copyBtn.classList.add('copied');
            setTimeout(() => {
              span.textContent = orig;
              copyBtn.classList.remove('copied');
            }, 1800);
          });
        });
      }
    });

    /* TOC Scrollspy */
    const tocLinks = document.querySelectorAll('.toc-sidebar .toc-link');
    if (tocLinks.length && 'IntersectionObserver' in window) {
      const sectionMap = {};
      tocLinks.forEach((a) => {
        const id = a.getAttribute('href').replace('#', '');
        sectionMap[id] = a;
      });

      const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            tocLinks.forEach((l) => l.classList.remove('active'));
            const link = sectionMap[entry.target.id];
            if (link) link.classList.add('active');
          }
        });
      }, { rootMargin: '-80px 0px -65% 0px' });

      document.querySelectorAll('.doc-section[id]').forEach((sec) => observer.observe(sec));
    }

    /* Voice Commands Search & Filter */
    const cmdInput = document.getElementById('cmd-search-input');
    const filterPills = document.querySelectorAll('[data-cmd-filter]');
    const cmdRows = document.querySelectorAll('.cmd-row');

    function filterCommands() {
      const query = cmdInput ? cmdInput.value.toLowerCase().trim() : '';
      const activePill = document.querySelector('[data-cmd-filter].active');
      const category = activePill ? activePill.getAttribute('data-cmd-filter') : 'all';

      cmdRows.forEach((row) => {
        const text = row.textContent.toLowerCase();
        const rowCat = row.getAttribute('data-cat') || '';
        const matchesQuery = !query || text.includes(query);
        const matchesCat = category === 'all' || rowCat.includes(category);

        if (matchesQuery && matchesCat) {
          row.style.display = '';
        } else {
          row.style.display = 'none';
        }
      });
    }

    if (cmdInput) cmdInput.addEventListener('input', filterCommands);
    filterPills.forEach((pill) => {
      pill.addEventListener('click', function () {
        filterPills.forEach((p) => p.classList.remove('active'));
        pill.classList.add('active');
        filterCommands();
      });
    });

    /* Command Palette / Search Modal */
    const searchModal = document.getElementById('search-modal');
    const modalInput = document.getElementById('modal-search-input');
    const modalResults = document.getElementById('modal-results');
    const searchTriggers = document.querySelectorAll('[data-search-trigger]');

    // Pre-indexed documentation search items
    const searchableItems = [
      { title: 'Overview & Philosophy', cat: 'Docs', href: '#overview', desc: 'Fail loudly, never optimistically' },
      { title: 'Quickstart Guide (3 Steps)', cat: 'Guide', href: '#quickstart', desc: 'Run on Windows, Pi, Linux or laptop' },
      { title: '3-Tier Safety Architecture', cat: 'Architecture', href: '#architecture', desc: 'Tier 0 Reflex, Tier 1 Edge, Tier 2 Cloud' },
      { title: 'Tier 0: Ultrasonic Reflex', cat: 'Hardware', href: '#tier0-reflex', desc: 'HC-SR04, ESP32, PCM5102 DAC alerts' },
      { title: 'Tier 1: Edge Vision (YOLOv8)', cat: 'AI Vision', href: '#tier1-edge', desc: 'Raspberry Pi ONNX offline object detection' },
      { title: 'Tier 2: Companion & Android Shell', cat: 'Companion', href: '#tier2-companion', desc: 'Wake word, memory, 7 neural languages' },
      { title: 'Hardware Wiring V2 (Buck & Common Ground)', cat: 'Hardware', href: '#hardware-wiring', desc: '5V 3A single rail power & pinouts' },
      { title: 'HC-SR04 Voltage Divider Circuit', cat: 'Hardware', href: '#ultrasonic-pins', desc: '1kΩ and 2kΩ resistors protect GPIO' },
      { title: 'Main ESP32 DevKit V1 Pinouts', cat: 'Hardware', href: '#main-esp32-pins', desc: 'GPIO 26/25/22 DAC, 13/14/27 Trig, 34/35/39 Echo' },
      { title: 'ESP32-CAM AI Thinker Setup', cat: 'Hardware', href: '#cam-pins', desc: 'ESP-NOW wireless head-camera JPEG uplink' },
      { title: 'Bill of Materials (BOM)', cat: 'Hardware', href: '#bom', desc: 'Complete commodity component listing' },
      { title: 'Firmware Synchronization Contract', cat: 'Firmware', href: '#firmware-variants', desc: 'PlatformIO, Arduino IDE, PowerShell' },
      { title: '12 Audited Safety Rules', cat: 'Firmware', href: '#safety-rules', desc: 'Zero dead branches, stall recovery, fail-closed' },
      { title: 'Voice Commands: "hey BlindSight"', cat: 'Voice', href: '#voice-commands', desc: 'Wake word continuous listening' },
      { title: 'Voice Commands: "remember that ..."', cat: 'Voice', href: '#voice-commands', desc: 'Persistent 20-fact companion memory' },
      { title: 'Voice Commands: "what is around me"', cat: 'Voice', href: '#voice-commands', desc: 'Camera scene analysis and audio response' },
      { title: 'Voice Commands: "language hindi"', cat: 'Voice', href: '#voice-commands', desc: 'Switch neural TTS reply language' },
      { title: 'REST API: /api/config', cat: 'API', href: '#api-config', desc: 'Token requirement and frame freshness limit' },
      { title: 'REST API: /api/prefs', cat: 'API', href: '#api-prefs', desc: 'Get and update language/voice preferences' },
      { title: 'REST API: /api/chat', cat: 'API', href: '#api-chat', desc: 'Send prompt with optional JPEG, receive audio' },
      { title: 'REST API: /api/latest-frame', cat: 'API', href: '#api-frame', desc: 'Freshest camera frame (<=30s), 404 on none' },
      { title: 'Android Shell APK Installation', cat: 'Android', href: '#android-app', desc: 'Scoped self-signed SSL trust, background mic' },
      { title: 'Automated CI & 93 Tests Summary', cat: 'Tests', href: '#testing-audit', desc: 'Anti-fake WAV verification, contract tests' }
    ];

    function openSearch() {
      if (searchModal) {
        searchModal.classList.add('open');
        modalInput.value = '';
        renderResults('');
        setTimeout(() => modalInput.focus(), 60);
      }
    }

    function closeSearch() {
      if (searchModal) searchModal.classList.remove('open');
    }

    function renderResults(q) {
      if (!modalResults) return;
      const lower = q.toLowerCase().trim();
      const matches = searchableItems.filter(item => {
        return !lower || item.title.toLowerCase().includes(lower) || item.desc.toLowerCase().includes(lower) || item.cat.toLowerCase().includes(lower);
      });

      if (!matches.length) {
        modalResults.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--text-faint);">No matching documentation found.</div>';
        return;
      }

      modalResults.innerHTML = matches.map((m, idx) => `
        <a href="${m.href}" class="search-item ${idx === 0 ? 'selected' : ''}" data-idx="${idx}">
          <div class="search-item-left">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
            <div>
              <div class="search-item-title">${m.title}</div>
              <div style="font-size: 12px; color: var(--text-muted);">${m.desc}</div>
            </div>
          </div>
          <span class="search-item-cat">${m.cat}</span>
        </a>
      `).join('');

      modalResults.querySelectorAll('.search-item').forEach(item => {
        item.addEventListener('click', closeSearch);
      });
    }

    searchTriggers.forEach(btn => btn.addEventListener('click', openSearch));

    if (modalInput) {
      modalInput.addEventListener('input', () => renderResults(modalInput.value));
      modalInput.addEventListener('keydown', (e) => {
        const items = modalResults.querySelectorAll('.search-item');
        let selIdx = Array.from(items).findIndex(el => el.classList.contains('selected'));
        if (e.key === 'ArrowDown') {
          e.preventDefault();
          if (selIdx < items.length - 1) {
            items[selIdx]?.classList.remove('selected');
            items[selIdx + 1]?.classList.add('selected');
            items[selIdx + 1]?.scrollIntoView({ block: 'nearest' });
          }
        } else if (e.key === 'ArrowUp') {
          e.preventDefault();
          if (selIdx > 0) {
            items[selIdx]?.classList.remove('selected');
            items[selIdx - 1]?.classList.add('selected');
            items[selIdx - 1]?.scrollIntoView({ block: 'nearest' });
          }
        } else if (e.key === 'Enter') {
          e.preventDefault();
          const target = items[selIdx];
          if (target) {
            target.click();
            window.location.hash = target.getAttribute('href');
            closeSearch();
          }
        } else if (e.key === 'Escape') {
          closeSearch();
        }
      });
    }

    if (searchModal) {
      searchModal.addEventListener('click', (e) => {
        if (e.target === searchModal) closeSearch();
      });
    }

    // Global shortcut Ctrl+K / Cmd+K
    document.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        if (searchModal && searchModal.classList.contains('open')) {
          closeSearch();
        } else {
          openSearch();
        }
      }
      if (e.key === 'Escape') {
        closeSearch();
      }
    });

  });
})();
