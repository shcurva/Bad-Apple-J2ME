import mido
import sys

def shift_midi(input_file, output_file, shift_seconds):
    mid = mido.MidiFile(input_file)
    
    new_mid = mido.MidiFile(type=mid.type, ticks_per_beat=mid.ticks_per_beat)
    
    for track in mid.tracks:
        new_track = mido.MidiTrack()
        absolute_time = 0
        shifted = []
        
        # 1. Переводим дельта-времена в абсолютные
        for msg in track:
            absolute_time += msg.time
            shifted.append(msg.copy(time=absolute_time))
        
        # 2. Находим темп (по умолчанию 120 BPM = 500000 мкс на удар)
        tempo = 500000
        for msg in shifted:
            if msg.type == 'set_tempo':
                tempo = msg.tempo
                break
        
        # 3. Считаем, сколько тиков в 1 секунду при этом темпе
        ticks_per_sec = (1000000.0 / tempo) * mid.ticks_per_beat
        shift_ticks = int(shift_seconds * ticks_per_sec)
        
        # 4. Применяем сдвиг и переводим обратно в дельта-времена
        prev_time = 0
        for msg in shifted:
            new_abs = max(0, msg.time + shift_ticks) # не уходим в минус
            delta = int(round(new_abs - prev_time))
            new_track.append(msg.copy(time=delta))
            prev_time = new_abs
            
        new_mid.tracks.append(new_track)
        
    new_mid.save(output_file)
    print(f"OK: shifted by {shift_seconds}s -> {output_file}")

if __name__ == '__main__':
    if len(sys.argv) != 4:
        print("Usage: python shift_midi.py <input.mid> <output.mid> <shift_seconds>")
        sys.exit(1)
    shift_midi(sys.argv[1], sys.argv[2], float(sys.argv[3]))
