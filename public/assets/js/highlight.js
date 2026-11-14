/* ==========================================================================
   BlindSight — Syntax Highlighter (Dependency-free, Safe HTML escaping)
   Supports: Bash/Shell, C++/Arduino, Python, JSON, Plain text
   ========================================================================== */
(function (root) {
  'use strict';

  const esc = (s) => s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const BASH_COMMANDS = new Set([
    'git', 'clone', 'cd', 'ls', 'cat', 'python', 'python3', 'pip', 'npm', 'run',
    'install', 'build', 'dev', 'start', 'curl', 'wget', 'export', 'echo', 'mkdir',
    'sudo', 'systemctl', 'chmod', 'source', 'deactivate'
  ]);

  const CPP_KEYWORDS = new Set([
    'const', 'void', 'int', 'char', 'bool', 'uint8_t', 'uint16_t', 'uint32_t',
    'size_t', 'float', 'double', 'if', 'else', 'while', 'for', 'return', 'include',
    'define', 'ifdef', 'ifndef', 'endif', 'static', 'struct', 'class', 'public',
    'private', 'true', 'false', 'NULL', 'nullptr'
  ]);

  function highlightBash(code) {
    const lines = code.split('\n');
    return lines.map(line => {
      // Comments
      if (/^\s*#/.test(line)) {
        return `<span class="tok-com">${esc(line)}</span>`;
      }
      // Prompts
      let processed = esc(line);
      processed = processed.replace(/^(\$|&gt;)\s/, '<span class="tok-prompt">$1 </span>');
      // Highlight strings
      processed = processed.replace(/"([^"]*)"/g, '<span class="tok-str">"$1"</span>');
      processed = processed.replace(/'([^']*)'/g, "<span class=\"tok-str\">'$1'</span>");
      // Highlight known commands
      BASH_COMMANDS.forEach(cmd => {
        const reg = new RegExp(`\\b(${cmd})\\b`, 'g');
        processed = processed.replace(reg, '<span class="tok-kw">$1</span>');
      });
      return processed;
    }).join('\n');
  }

  function highlightCpp(code) {
    let out = esc(code);
    // Comments
    out = out.replace(/(\/\/[^\n]*)/g, '<span class="tok-com">$1</span>');
    // Strings
    out = out.replace(/(".*?")/g, '<span class="tok-str">$1</span>');
    // Preprocessor
    out = out.replace(/(#\s*\w+)/g, '<span class="tok-type">$1</span>');
    // Keywords
    CPP_KEYWORDS.forEach(kw => {
      const reg = new RegExp(`\\b(${kw})\\b`, 'g');
      out = out.replace(reg, '<span class="tok-kw">$1</span>');
    });
    // Numbers
    out = out.replace(/\b(\d+)\b/g, '<span class="tok-num">$1</span>');
    return out;
  }

  function highlightJson(code) {
    let out = esc(code);
    out = out.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
      let cls = 'tok-num';
      if (/^"/.test(match)) {
        if (/:$/.test(match)) {
          cls = 'tok-kw'; // key
        } else {
          cls = 'tok-str'; // string value
        }
      } else if (/true|false/.test(match)) {
        cls = 'tok-type';
      } else if (/null/.test(match)) {
        cls = 'tok-com';
      }
      return `<span class="${cls}">${match}</span>`;
    });
    return out;
  }

  function highlight(code, lang) {
    lang = (lang || '').toLowerCase();
    if (lang === 'bash' || lang === 'sh' || lang === 'terminal' || lang === 'shell') {
      return highlightBash(code);
    }
    if (lang === 'cpp' || lang === 'c' || lang === 'arduino' || lang === 'ino') {
      return highlightCpp(code);
    }
    if (lang === 'json') {
      return highlightJson(code);
    }
    return esc(code);
  }

  root.BlindSightHL = { highlight };
})(typeof window !== 'undefined' ? window : this);
