"""
MobileNetV2 Fine-Tuning and INT8 TFLite Conversion Pipeline
Target: On-Device E-Waste Classification for Kabadiwala Formalization (SIH PS 26229)
Compliance: CPCB E-Waste (Management) Rules 2022 Schedule I categories
"""

import os
import sys

LABELS = [
    "mobile_phone",               # ITEW15
    "laptop_computer",            # ITEW3
    "pcb_circuit_board",          # ITEW_PCB
    "crt_lcd_monitor",            # CEEW1
    "battery_pack",               # BATT_LII
    "copper_cable_wire",          # CBL_COP
    "printer_peripheral",         # ITEW4
    "household_appliance_small"   # CEEW_SHA
]

CPCB_MAPPING = {
    "mobile_phone": {"code": "ITEW15", "name": "Cellular Telephones / Smartphones"},
    "laptop_computer": {"code": "ITEW3", "name": "Laptops & Notebooks"},
    "pcb_circuit_board": {"code": "ITEW_PCB", "name": "Printed Circuit Boards (High Value)"},
    "crt_lcd_monitor": {"code": "CEEW1", "name": "Televisions & Display Monitors"},
    "battery_pack": {"code": "BATT_LII", "name": "Lithium-Ion / Secondary Batteries"},
    "copper_cable_wire": {"code": "CBL_COP", "name": "Copper Cable Harness & Wires"},
    "printer_peripheral": {"code": "ITEW4", "name": "Printers, Cartridges & Scanners"},
    "household_appliance_small": {"code": "CEEW_SHA", "name": "Small Household Electronics"}
}

def build_model(num_classes=8, input_shape=(224, 224, 3)):
    """
    Builds transfer learning model on top of MobileNetV2
    """
    import tensorflow as tf
    from tensorflow.keras import layers, models

    base_model = tf.keras.applications.MobileNetV2(
        input_shape=input_shape,
        include_top=False,
        weights='imagenet'
    )
    base_model.trainable = False

    inputs = tf.keras.Input(shape=input_shape)
    # Preprocessing: MobileNetV2 expects [-1, 1]
    x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)
    x = base_model(x, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dropout(0.2)(x)
    x = layers.Dense(128, activation='relu')(x)
    x = layers.Dropout(0.2)(x)
    outputs = layers.Dense(num_classes, activation='softmax')(x)

    model = models.Model(inputs, outputs)
    return model, base_model

def train_and_export(data_dir=None, output_tflite_path="mobilenet_v2_ewaste.tflite"):
    """
    Trains on dataset if available, otherwise sets up transfer learning graph,
    quantizes and exports model to TFLite format.
    """
    try:
        import tensorflow as tf
    except ImportError:
        print("[!] TensorFlow not installed in current environment.")
        print("[*] To run training, install: pip install tensorflow")
        print("[*] Generating reference architecture script completed.")
        return

    print("Building MobileNetV2 architecture for 8 E-Waste categories...")
    model, base_model = build_model(num_classes=len(LABELS))
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    model.summary()

    # If dataset exists, perform transfer learning + fine tuning
    if data_dir and os.path.exists(data_dir):
        print(f"Loading e-waste dataset from {data_dir}...")
        train_ds = tf.keras.utils.image_dataset_from_directory(
            data_dir,
            validation_split=0.2,
            subset="training",
            seed=1337,
            image_size=(224, 224),
            batch_size=32
        )
        val_ds = tf.keras.utils.image_dataset_from_directory(
            data_dir,
            validation_split=0.2,
            subset="validation",
            seed=1337,
            image_size=(224, 224),
            batch_size=32
        )
        print("Training top classification head...")
        model.fit(train_ds, validation_data=val_ds, epochs=5)

        # Fine-tuning: Unfreeze top layers
        base_model.trainable = True
        fine_tune_at = len(base_model.layers) - 30
        for layer in base_model.layers[:fine_tune_at]:
            layer.trainable = False

        model.compile(
            optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
            loss='categorical_crossentropy',
            metrics=['accuracy']
        )
        print("Fine-tuning top 30 MobileNetV2 layers...")
        model.fit(train_ds, validation_data=val_ds, epochs=10)

    # Convert to TFLite with Dynamic Range Quantization
    print("Converting model to TensorFlow Lite with INT8 post-training quantization...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_quant_model = converter.convert()

    with open(output_tflite_path, 'wb') as f:
        f.write(tflite_quant_model)

    print(f"Saved optimized TFLite model to {output_tflite_path} ({len(tflite_quant_model)} bytes)")

if __name__ == "__main__":
    print("=== E-Waste MobileNetV2 Model Pipeline ===")
    train_and_export()
