/* ==========================================================================
   BlindSight — Ultra Lightweight Scripts
   Zero Lag • No Layout Thrashing • Smooth Navigation
   ========================================================================== */

(function () {
  'use strict';

  // Theme Initializer
  const savedTheme = (() => {
    try { return localStorage.getItem('blindsight-theme'); } catch (e) { return null; }
  })();
  const initialTheme = savedTheme || 'dark';
  document.documentElement.setAttribute('data-theme', initialTheme);

  window.setTheme = function (t) {
    document.documentElement.setAttribute('data-theme', t);
    try { localStorage.setItem('blindsight-theme', t); } catch (e) {}
    document.querySelectorAll('[data-theme-toggle]').forEach((btn) => {
      btn.setAttribute('aria-label', t === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
    });
  };

  document.addEventListener('DOMContentLoaded', function () {
    // Theme Toggle
    document.querySelectorAll('[data-theme-toggle]').forEach((btn) => {
      btn.addEventListener('click', function () {
        const cur = document.documentElement.getAttribute('data-theme');
        window.setTheme(cur === 'dark' ? 'light' : 'dark');
      });
    });

    // Mobile Nav Toggle
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

    // Code Copy Helpers
    document.querySelectorAll('[data-copy]').forEach((btn) => {
      btn.addEventListener('click', function () {
        const text = btn.getAttribute('data-copy');
        if (text && navigator.clipboard) {
          navigator.clipboard.writeText(text).then(() => {
            const orig = btn.innerHTML;
            btn.textContent = 'Copied!';
            setTimeout(() => { btn.innerHTML = orig; }, 1800);
          });
        }
      });
    });
  });
})();
