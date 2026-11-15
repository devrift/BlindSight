from http.server import BaseHTTPRequestHandler
import json
import asyncio
import urllib.parse
import io

try:
    import edge_tts
    HAS_EDGE = True
except Exception:
    HAS_EDGE = False

class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)
            body = json.loads(post_data.decode('utf-8'))
            text = body.get('text', '').strip()
            voice = body.get('voice', 'en-GB-RyanNeural')
            lang = body.get('lang', 'en').lower()

            if not text:
                self.send_response(400)
                self.end_headers()
                return

            # Persona voice mapping
            voice_map = {
                'en': 'en-GB-RyanNeural',
                'hi': 'hi-IN-SwaraNeural',
                'bn': 'bn-IN-TanishaaNeural',
                'ta': 'ta-IN-PallaviNeural',
                'te': 'te-IN-ShrutiNeural',
                'es': 'es-ES-AlvaroNeural',
                'fr': 'fr-FR-HenriNeural',
                'jarvis': 'en-GB-RyanNeural',
                'aria': 'en-US-AriaNeural',
                'guy': 'en-US-GuyNeural',
                'swara': 'hi-IN-SwaraNeural'
            }

            selected_voice = voice if voice and 'Neural' in voice else voice_map.get(lang, 'en-GB-RyanNeural')

            async def generate_speech():
                comm = edge_tts.Communicate(text, selected_voice)
                audio_buf = bytearray()
                async for chunk in comm.stream():
                    if chunk['type'] == 'audio':
                        audio_buf.extend(chunk['data'])
                return bytes(audio_buf)

            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            audio_bytes = loop.run_until_complete(generate_speech())
            loop.close()

            if not audio_bytes:
                self.send_response(500)
                self.end_headers()
                return

            self.send_response(200)
            self.send_header('Content-Type', 'audio/mpeg')
            self.send_header('Content-Length', str(len(audio_bytes)))
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Cache-Control', 'public, max-age=86400')
            self.end_headers()
            self.wfile.write(audio_bytes)

        except Exception as e:
            self.send_response(500)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({'error': str(e)}).encode('utf-8'))

    def do_GET(self):
        try:
            parsed = urllib.parse.urlparse(self.path)
            params = urllib.parse.parse_qs(parsed.query)
            text = params.get('text', [''])[0].strip()
            voice = params.get('voice', ['en-GB-RyanNeural'])[0]
            lang = params.get('lang', ['en'])[0].lower()

            if not text:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'BlindSight Edge-TTS active'}).encode('utf-8'))
                return

            async def generate_speech():
                comm = edge_tts.Communicate(text, voice)
                audio_buf = bytearray()
                async for chunk in comm.stream():
                    if chunk['type'] == 'audio':
                        audio_buf.extend(chunk['data'])
                return bytes(audio_buf)

            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            audio_bytes = loop.run_until_complete(generate_speech())
            loop.close()

            self.send_response(200)
            self.send_header('Content-Type', 'audio/mpeg')
            self.send_header('Content-Length', str(len(audio_bytes)))
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(audio_bytes)

        except Exception as e:
            self.send_response(500)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({'error': str(e)}).encode('utf-8'))

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
