from http.server import BaseHTTPRequestHandler
import json
import asyncio
import urllib.parse
import os

try:
    import edge_tts
    HAS_EDGE = True
except Exception:
    HAS_EDGE = False

class handler(BaseHTTPRequestHandler):
    def _send_audio(self, audio_bytes):
        self.send_response(200)
        self.send_header('Content-Type', 'audio/mpeg')
        self.send_header('Content-Length', str(len(audio_bytes)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Cache-Control', 'public, max-age=86400')
        self.end_headers()
        self.wfile.write(audio_bytes)

    def _send_error(self, code, msg):
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps({'error': msg}).encode('utf-8'))

    def _resolve_voice(self, voice, lang):
        voice_map = {
            'en': 'en-US-AriaNeural',
            'hi': 'hi-IN-SwaraNeural',
            'bn': 'bn-IN-TanishaaNeural',
            'ta': 'ta-IN-PallaviNeural',
            'te': 'te-IN-ShrutiNeural',
            'es': 'es-ES-AlvaroNeural',
            'fr': 'fr-FR-HenriNeural',
            'jarvis': 'en-US-AriaNeural',
            'aria': 'en-US-AriaNeural',
            'guy': 'en-US-GuyNeural',
            'swara': 'hi-IN-SwaraNeural'
        }
        if voice and 'Neural' in voice:
            return voice
        return voice_map.get((lang or 'en').lower(), 'en-US-AriaNeural')

    async def _generate_audio(self, text, voice):
        comm = edge_tts.Communicate(text, voice)
        audio_buf = bytearray()
        async for chunk in comm.stream():
            if chunk['type'] == 'audio':
                audio_buf.extend(chunk['data'])
        return bytes(audio_buf)

    def do_HEAD(self):
        self.send_response(200)
        self.send_header('Content-Type', 'audio/mpeg')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()

    def do_GET(self):
        try:
            parsed = urllib.parse.urlparse(self.path)
            params = urllib.parse.parse_qs(parsed.query)
            text = params.get('text', [''])[0].strip()
            voice = params.get('voice', [''])[0]
            lang = params.get('lang', ['en'])[0].lower()

            if not text:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'BlindSight Edge-TTS active'}).encode('utf-8'))
                return

            selected_voice = self._resolve_voice(voice, lang)
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            audio = loop.run_until_complete(self._generate_audio(text, selected_voice))
            loop.close()

            if audio:
                self._send_audio(audio)
            else:
                self._send_error(500, "Empty audio generated")
        except Exception as e:
            self._send_error(500, str(e))

    def do_POST(self):
        try:
            content_length = int(self.headers.get('Content-Length', 0))
            raw_body = self.rfile.read(content_length).decode('utf-8', errors='ignore') if content_length > 0 else ""

            text = ""
            voice = ""
            lang = "en"

            # Parse JSON or fallback
            if raw_body.startswith('{'):
                try:
                    data = json.loads(raw_body)
                    text = str(data.get('text', '')).strip()
                    voice = str(data.get('voice', '')).strip()
                    lang = str(data.get('lang', 'en')).strip().lower()
                except Exception:
                    pass

            if not text and '=' in raw_body:
                params = urllib.parse.parse_qs(raw_body)
                text = params.get('text', [''])[0].strip()
                voice = params.get('voice', [''])[0]
                lang = params.get('lang', ['en'])[0].lower()

            if not text:
                # Also check URL query params
                parsed = urllib.parse.urlparse(self.path)
                params = urllib.parse.parse_qs(parsed.query)
                text = params.get('text', [''])[0].strip()
                if not voice:
                    voice = params.get('voice', [''])[0]

            if not text:
                self._send_error(400, "Missing 'text' parameter")
                return

            selected_voice = self._resolve_voice(voice, lang)
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            audio = loop.run_until_complete(self._generate_audio(text, selected_voice))
            loop.close()

            if audio:
                self._send_audio(audio)
            else:
                self._send_error(500, "Empty audio generated")
        except Exception as e:
            self._send_error(500, str(e))

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, User-Agent')
        self.end_headers()
