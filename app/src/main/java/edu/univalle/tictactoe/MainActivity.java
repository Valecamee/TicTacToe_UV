package edu.univalle.tictactoe;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private static final int REQUEST_PERMISSION_CODE = 100;

    private Button buttonIniciar;
    private RadioButton radioButtonServidor;
    private TextView textBtStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonIniciar = findViewById(R.id.buttonIniciar);
        radioButtonServidor = findViewById(R.id.radioButtonServidor);
        EditText editRoomName = findViewById(R.id.editRoomName);

        radioButtonServidor.setOnCheckedChangeListener((g, checked) -> {
            if (checked) {
                editRoomName.setVisibility(View.VISIBLE);
            } else {
                editRoomName.setVisibility(View.GONE);
            }
        });

        if (radioButtonServidor.isChecked()) {
            editRoomName.setVisibility(View.VISIBLE);
        }

        textBtStatus = findViewById(R.id.textBtStatus);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            textBtStatus.setText(getString(R.string.bluetooth_no_soportado));
            Toast.makeText(this, getString(R.string.bluetooth_no_soportado_toast), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (verificarPermisos()) {
            habilitarBluetooth();
        }
    }

    private boolean verificarPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ solo necesita permisos de Bluetooth
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.BLUETOOTH_SCAN)
                            != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                android.Manifest.permission.BLUETOOTH_CONNECT,
                                android.Manifest.permission.BLUETOOTH_SCAN
                        },
                        REQUEST_PERMISSION_CODE);

                return false;
            }
        } else {
            // Android 11 y anteriores: necesitan ubicación para escanear Bluetooth
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        REQUEST_PERMISSION_CODE);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_CODE) {
            boolean ok = true;

            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    ok = false;
                    break;
                }
            }

            if (ok) {
                habilitarBluetooth();
            } else {
                textBtStatus.setText(getString(R.string.permisos_necesarios));
                Toast.makeText(this, getString(R.string.permisos_requeridos), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void habilitarBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            lanzadorBluetooth.launch(intent);
        } else {
            habilitarBotonIniciar();
        }
    }

    private final ActivityResultLauncher<Intent> lanzadorBluetooth =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            habilitarBotonIniciar();
                        } else {
                            textBtStatus.setText(getString(R.string.bluetooth_no_habilitado));
                            Toast.makeText(this, getString(R.string.bluetooth_no_habilitado_toast), Toast.LENGTH_SHORT).show();
                        }
                    }
            );

    private final ActivityResultLauncher<Intent> lanzadorDiscoverable =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() >= 0) {
                            // El dispositivo está descubrible, iniciar partida
                            iniciarPartidaServidor();
                        } else {
                            textBtStatus.setText(getString(R.string.bluetooth_no_habilitado));
                            Toast.makeText(this, getString(R.string.dispositivo_descubrible), Toast.LENGTH_LONG).show();
                        }
                    }
            );

    private void habilitarBotonIniciar() {
        buttonIniciar.setEnabled(true);
        textBtStatus.setText(getString(R.string.bt_enabled));
    }

    // --------------------------
    //  CAMBIAR IDIOMA
    // --------------------------
    public void cambiarIdioma(View view) {
        Locale localeActual = getResources().getConfiguration().locale;
        Locale nuevoLocale;
        
        if (localeActual.getLanguage().equals("es")) {
            nuevoLocale = Locale.ENGLISH;
        } else {
            nuevoLocale = new Locale("es");
        }
        
        Locale.setDefault(nuevoLocale);
        Configuration config = new Configuration();
        config.locale = nuevoLocale;
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        
        // Reiniciar la actividad para aplicar los cambios
        recreate();
    }

    // --------------------------
    //  BOTÓN INICIAR PARTIDA
    // --------------------------
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void iniciarPartida(View view) {

        if (radioButtonServidor.isChecked()) {
            // Servidor: cambiar nombre y hacer descubrible
            EditText editRoomName = findViewById(R.id.editRoomName);
            String roomName = editRoomName.getText().toString().trim();

            if (roomName.isEmpty()) roomName = "TICTACTOE_ROOM_DEFAULT";

            roomName = "TICTACTOE_ROOM_" + roomName;
            bluetoothAdapter.setName(roomName);

            // Hacer el dispositivo descubrible para que otros puedan encontrarlo
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            // Hacer descubrible por 300 segundos (5 minutos)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            lanzadorDiscoverable.launch(discoverableIntent);

        } else {
            // Cliente → va a ListActivity a ver salas
            Intent intent = new Intent(this, ListActivity.class);
            startActivity(intent);
        }
    }

    private void iniciarPartidaServidor() {
        EditText editRoomName = findViewById(R.id.editRoomName);
        String roomName = editRoomName.getText().toString().trim();

        if (roomName.isEmpty()) roomName = "TICTACTOE_ROOM_DEFAULT";
        roomName = "TICTACTOE_ROOM_" + roomName;

        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("role", "SERVER");
        intent.putExtra("roomName", roomName);
        intent.putExtra("address", "SERVER");
        startActivity(intent);
    }
}
