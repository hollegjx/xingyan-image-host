const http = require('http')
const json = { 'Content-Type': 'application/json' }

http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://local.invalid')
  const send = (status, body) => { res.writeHead(status, json); res.end(JSON.stringify(body)) }
  if (req.method === 'POST' && url.pathname === '/reset') {
    await pm.state.set('ai-provider.calls', 0)
    return send(200, { ok: true })
  }
  if (req.method === 'GET' && url.pathname === '/stats')
    return send(200, { calls: (await pm.state.get('ai-provider.calls')) || 0 })
  if (req.method === 'GET' && url.pathname === '/result/image.png') {
    res.writeHead(200, { 'Content-Type': 'image/png' })
    return res.end(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6hGAAAAAASUVORK5CYII=', 'base64'))
  }
  if (req.method === 'GET' && url.pathname === '/result/error.png') {
    res.writeHead(503, { 'Content-Type': 'text/html' })
    return res.end('<h1>Unavailable</h1>')
  }
  if (req.method !== 'POST' || url.pathname !== '/api/v1/services/aigc/multimodal-generation/generation')
    return send(501, { error: 'Not modeled' })
  await pm.state.set('ai-provider.calls', ((await pm.state.get('ai-provider.calls')) || 0) + 1)
  const body = req.body
  if (!body || body.model !== 'qwen-image-3.0' || body.parameters?.n !== 1
    || body.parameters?.size !== '1024*1024' || !body.input?.messages?.[0]?.content?.[0]?.image)
    return send(400, { code: 'InvalidParameter', request_id: 'fixture-invalid' })
  switch (url.searchParams.get('case')) {
    case 'error-json': return send(500, { code: 'InternalError', request_id: 'fixture-json' })
    case 'error-html': res.writeHead(503, { 'Content-Type': 'text/html' }); return res.end('<h1>Unavailable</h1>')
    case 'drop': return req.socket.destroy()
    case 'rejected': return send(403, { code: 'AccessDenied', request_id: 'fixture-denied' })
    case 'receipt-error':
    case 'receipt': return send(200, { request_id: 'fixture-receipt', output: { choices: [{ message: { role: 'assistant', content: [{ image: `https://result.fixture.test/result/${url.searchParams.get('case') === 'receipt' ? 'image' : 'error'}.png` }] } }] } })
    default: return send(501, { error: 'Scenario not modeled' })
  }
}).listen(process.env.PORT)
