package edu.univalle.tictactoe; // ⚠️ CAMBIA ESTO por tu package real

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

        // Referencias a las vistas
        buttonIniciar = findViewById(R.id.buttonIniciar);
        radioButtonServidor = findViewById(R.id.radioButtonServidor);
        textBtStatus = findViewById(R.id.textBtStatus);

        // Obtener el adaptador Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Verificar si el dispositivo soporta Bluetooth
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_no_soportado,
                    Toast.LENGTH_LONG).show();
            textBtStatus.setText("[ERROR: NO BT SUPPORT]");
            textBtStatus.setTextColor(getResources().getColor(R.color.cyberpunk_magenta, null));
            finish();
        } else {
            // Verificar y solicitar permisos
            if (verificarPermisos()) {
                habilitarBluetooth();
            }
        }
    }

    // Método para verificar permisos
    private boolean verificarPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.BLUETOOTH_SCAN)
                            != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                android.Manifest.permission.BLUETOOTH_CONNECT,
                                android.Manifest.permission.BLUETOOTH_SCAN,
                                android.Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        REQUEST_PERMISSION_CODE);
                return false;
            }
        }
        return true;
    }

    // Callback cuando el usuario responde a la solicitud de permisos
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_CODE) {
            boolean todosOtorgados = true;
            for (int resultado : grantResults) {
                if (resultado != PackageManager.PERMISSION_GRANTED) {
                    todosOtorgados = false;
                    break;
                }
            }

            if (todosOtorgados) {
                habilitarBluetooth();
            } else {
                Toast.makeText(this, R.string.permisos_necesarios,
                        Toast.LENGTH_SHORT).show();
                textBtStatus.setText("[ERROR: PERMISSIONS DENIED]");
                textBtStatus.setTextColor(getResources().getColor(R.color.cyberpunk_magenta, null));
            }
        }
    }

    // Método para habilitar Bluetooth
    private void habilitarBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent habilitarIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            lanzadorBluetooth.launch(habilitarIntent);
        } else {
            Toast.makeText(this, R.string.bluetooth_habilitado,
                    Toast.LENGTH_SHORT).show();
            habilitarBotonIniciar();
        }
    }

    // Launcher para habilitar Bluetooth
    private final ActivityResultLauncher<Intent> lanzadorBluetooth =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            Toast.makeText(this, R.string.bluetooth_habilitado,
                                    Toast.LENGTH_SHORT).show();
                            habilitarBotonIniciar();
                        } else {
                            Toast.makeText(this, R.string.bluetooth_no_habilitado,
                                    Toast.LENGTH_SHORT).show();
                            textBtStatus.setText("[WARNING: BT DISABLED]");
                            textBtStatus.setTextColor(getResources().getColor(R.color.cyberpunk_yellow, null));
                        }
                    });

    // Habilitar el botón Iniciar
    private void habilitarBotonIniciar() {
        buttonIniciar.setEnabled(true);

        // Actualizar estado de Bluetooth
        TextView textBtStatus = findViewById(R.id.textBtStatus);
        textBtStatus.setText("[BT: ENABLED ⚡]");
        textBtStatus.setTextColor(getResources().getColor(R.color.cyberpunk_cyan, null));
    }

    // Metodo onClick del botón Iniciar
    public void iniciarPartida(View view) {
        Intent intent;

        if (radioButtonServidor.isChecked()) {
            intent = new Intent(this, GameActivity.class);
            intent.putExtra("role", "SERVER");
            intent.putExtra("address", "SERVER");
        } else {
            intent = new Intent(this, ListActivity.class);
        }

        startActivity(intent);
    }



}
