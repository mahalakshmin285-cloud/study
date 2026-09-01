const fs = require('fs');
const path = require('path');

const srcDir = path.resolve(__dirname);
const outDir = path.resolve(__dirname, 'dist');
const rootDistDir = path.resolve(__dirname, '..', 'dist');

console.log('[Build] Starting web compilation...');
console.log(`[Build] Source: ${srcDir}`);
console.log(`[Build] Target: ${outDir}`);

// Ensure output directories exist
if (!fs.existsSync(outDir)) {
  fs.mkdirSync(outDir, { recursive: true });
}
if (!fs.existsSync(rootDistDir)) {
  fs.mkdirSync(rootDistDir, { recursive: true });
}

// Read index.html
const indexHtmlPath = path.join(srcDir, 'index.html');
if (!fs.existsSync(indexHtmlPath)) {
  console.error('[Build Error] index.html not found in ' + srcDir);
  process.exit(1);
}

let htmlContent = fs.readFileSync(indexHtmlPath, 'utf8');

// Minify whitespace slightly for production output
const minifiedHtml = htmlContent
  .replace(/\n\s+/g, '\n')
  .trim();

// Write to both web/dist and root dist to support any Vercel configuration
fs.writeFileSync(path.join(outDir, 'index.html'), minifiedHtml, 'utf8');
fs.writeFileSync(path.join(rootDistDir, 'index.html'), minifiedHtml, 'utf8');

console.log(`[Build] Successfully compiled web assets:`);
console.log(` - ${path.join(outDir, 'index.html')} (${Buffer.byteLength(minifiedHtml)} bytes)`);
console.log(` - ${path.join(rootDistDir, 'index.html')} (${Buffer.byteLength(minifiedHtml)} bytes)`);
console.log('[Build] Compilation complete.');
