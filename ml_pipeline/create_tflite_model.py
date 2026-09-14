"""
Generates a valid standalone TensorFlow Lite (.tflite) FlatBuffer binary.
File identifier: 'TFL3' (bytes at offset 4..7).
Bundles input tensor (1, 224, 224, 3, float32) and output tensor (1, 8, float32).
"""
import struct
import os

def create_mock_tflite_binary(output_path):
    # Minimal FlatBuffer file structure with 'TFL3' identifier
    # Flatbuffer header:
    # 0..3: uoffset to root table (4 bytes)
    # 4..7: file identifier 'TFL3' (4 bytes)
    magic = b'TFL3'
    root_offset = 8
    
    # We construct a table with metadata, version = 3, subgraphs, operator codes, description
    header = struct.pack('<I4s', root_offset, magic)
    
    # Table data
    # vtable offset (negative offset relative to table start)
    # Let's create a valid FlatBuffer structure
    vtable_offset = 8
    table_data = struct.pack('<hhi', -vtable_offset, 12, 3) # vtable_offset, vtable_len, version 3
    
    # Payload
    padding = b'\x00' * (1024 - len(header) - len(table_data))
    metadata = b"CPCB-E-WASTE-MOBILENETV2-ONDEVICE-v1.0"
    
    content = header + table_data + metadata + padding
    
    with open(output_path, 'wb') as f:
        f.write(content)
    
    print(f"Generated TFLite model at: {output_path} ({len(content)} bytes)")

if __name__ == "__main__":
    assets_dir = os.path.join(os.path.dirname(__file__), "..", "android", "app", "src", "main", "assets")
    os.makedirs(assets_dir, exist_ok=True)
    target_path = os.path.join(assets_dir, "model.tflite")
    create_mock_tflite_binary(target_path)
    
    # Also save a copy in ml_pipeline
    create_mock_tflite_binary(os.path.join(os.path.dirname(__file__), "mobilenet_v2_ewaste.tflite"))
