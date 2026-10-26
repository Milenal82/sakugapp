import pathlib,struct,sys,unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
from screenshot_pixels import edge_background

class ScreencapTests(unittest.TestCase):
    def test_android_raw_headers_and_pixel_channel_orders(self):
        for format_id,pixel in [(1,bytes([245,246,248,255])),(2,bytes([245,246,248,0])),(3,bytes([245,246,248])),(5,bytes([248,246,245,255]))]:
            for extra in (b'',b'\0'*4):
                raw=struct.pack('<III',32,32,format_id)+extra+pixel*1024
                self.assertEqual([(245,246,248)]*12,edge_background(raw))
    def test_dark_pixels_and_corrupt_frames_cannot_pass_as_a_light_background(self):
        raw=struct.pack('<III',32,32,1)+bytes([16,17,20,255])*1024
        self.assertEqual([(16,17,20)]*12,edge_background(raw))
        for invalid in [b'',raw[:-4],struct.pack('<III',32,32,99)+b'0'*4096]:
            with self.assertRaises(ValueError):edge_background(invalid)
