const http = require('http');
const fs = require('fs/promises');
const path = require('path');

const rootDir = __dirname;
const defaultFile = process.argv[2] || 'viewer.html';
const port = Number(process.env.PORT || 4173);

const contentTypes = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
};

function send(res, statusCode, body, contentType = 'text/plain; charset=utf-8') {
  res.writeHead(statusCode, {
    'Content-Type': contentType,
    'Cache-Control': 'no-store',
  });
  res.end(body);
}

async function findFile(filePath) {
  try {
    const stat = await fs.stat(filePath);
    if (stat.isFile()) return filePath;
    if (stat.isDirectory()) {
      for (const candidate of ['index.html', 'viewer.html', defaultFile]) {
        const nested = path.join(filePath, candidate);
        try {
          const nestedStat = await fs.stat(nested);
          if (nestedStat.isFile()) return nested;
        } catch {
          // Ignore missing nested candidates.
        }
      }
    }
  } catch {
    return null;
  }
  return null;
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url || '/', 'http://localhost');
    const requestPath = decodeURIComponent(url.pathname);
    let targetPath = path.resolve(rootDir, '.' + requestPath);

    if (!targetPath.startsWith(rootDir)) {
      send(res, 403, 'Forbidden');
      return;
    }

    if (requestPath === '/') {
      targetPath = path.resolve(rootDir, defaultFile);
    }

    const resolved = await findFile(targetPath);
    if (!resolved) {
      send(res, 404, `Not found: ${requestPath}`);
      return;
    }

    const ext = path.extname(resolved).toLowerCase();
    const contentType = contentTypes[ext] || 'application/octet-stream';
    const body = await fs.readFile(resolved);
    res.writeHead(200, {
      'Content-Type': contentType,
      'Cache-Control': 'no-store',
    });
    res.end(body);
  } catch (error) {
    send(res, 500, `Server error: ${error.message}`);
  }
});

server.listen(port, () => {
  console.log(`Dev server running at http://localhost:${port}`);
});
