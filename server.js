const express = require('express');
const http = require('http');
const WebSocket = require('ws');

const app = express();
app.use(express.json());

const locations = {};

app.post('/location', (req, res) => {
    const { room, lat, lng } = req.body;
    if (!room || typeof lat !== 'number' || typeof lng !== 'number') {
        return res.status(400).json({ error: 'bad request' });
    }
    locations[room] = { lat, lng, time: Date.now() };
    res.json({ ok: true });
});

app.get('/location/:room', (req, res) => {
    const loc = locations[req.params.room];
    if (!loc) return res.status(404).json({ error: 'not found' });
    res.json(loc);
});

const server = http.createServer(app);
const wss = new WebSocket.Server({ server });
const waiting = {};

wss.on('connection', (ws) => {
    let room = null;

    ws.once('message', (msg) => {
        room = msg.toString().slice(0, 4);
        const peer = waiting[room];
        if (peer && peer.readyState === WebSocket.OPEN) {
            delete waiting[room];
            ws.peer = peer;
            peer.peer = ws;

            const relay = (from, to) => {
                from.on('message', (data) => {
                    if (to.readyState === WebSocket.OPEN) to.send(data);
                });
            };
            relay(ws, peer);
            relay(peer, ws);

            ws.send('PAIRED');
            peer.send('PAIRED');
            const hb = setInterval(() => {
                if (ws.readyState === WebSocket.OPEN && peer.readyState === WebSocket.OPEN) {
                    ws.send('HB'); peer.send('HB');
                } else {
                    clearInterval(hb);
                }
            }, 2000);
        } else {
            waiting[room] = ws;
        }
    });

    ws.on('close', () => {
        if (room && waiting[room] === ws) delete waiting[room];
        if (ws.peer) ws.peer.close();
    });
    ws.on('error', () => {});
});

const PORT = process.env.PORT || 10000;
server.listen(PORT, () => console.log('Server chal raha hai, port ' + PORT));
