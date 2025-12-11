package edu.univalle.tictactoe;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class GameActivity extends AppCompatActivity {

    private TextView textTurnStatus;
    private Button[] buttons = new Button[9];

    private String role;
    private String serverAddress;

    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;

    private final UUID APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private boolean myTurn = false;
    private char mySymbol;
    private char enemySymbol;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        textTurnStatus = findViewById(R.id.textTurnStatus);

        buttons[0] = findViewById(R.id.b0);
        buttons[1] = findViewById(R.id.b1);
        buttons[2] = findViewById(R.id.b2);
        buttons[3] = findViewById(R.id.b3);
        buttons[4] = findViewById(R.id.b4);
        buttons[5] = findViewById(R.id.b5);
        buttons[6] = findViewById(R.id.b6);
        buttons[7] = findViewById(R.id.b7);
        buttons[8] = findViewById(R.id.b8);

        // Recibir parámetros
        role = getIntent().getStringExtra("role");
        serverAddress = getIntent().getStringExtra("address");

        configurarRol();
        conectarBluetooth();
    }

    private void configurarRol() {
        if (role.equals("SERVER")) {
            mySymbol = 'X';
            enemySymbol = 'O';
            myTurn = true;
            textTurnStatus.setText("[YOUR TURN - X]");
        } else {
            mySymbol = 'O';
            enemySymbol = 'X';
            myTurn = false;
            textTurnStatus.setText("[WAITING FOR MOVE]");
        }
    }

    private void conectarBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(serverAddress);

        new Thread(() -> {
            try {
                socket = device.createRfcommSocketToServiceRecord(APP_UUID);
                socket.connect();

                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();

                escucharJugadas();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
    private void iniciarServidor() {
        new Thread(() -> {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

                // UUID clásico para SPP
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                BluetoothServerSocket serverSocket =
                        adapter.listenUsingRfcommWithServiceRecord("TicTacToeUV", uuid);

                runOnUiThread(() -> textTurnStatus.setText("[SERVER: WAITING FOR CLIENT...]"));

                socket = serverSocket.accept();  // BLOQUEA aquí hasta que alguien se conecte

                serverSocket.close();

                abrirStreams();
                iniciarRecepcion();

                runOnUiThread(() -> textTurnStatus.setText("[CLIENT CONNECTED ✓]"));

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        textTurnStatus.setText("[SERVER ERROR ❌]"));
            }
        }).start();
    }


    private void enviarJugada(int index) {
        try {
            outputStream.write(index);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void escucharJugadas() {
        byte[] buffer = new byte[1];

        while (true) {
            try {
                int bytes = inputStream.read(buffer);
                if (bytes > 0) {

                    int index = buffer[0];

                    runOnUiThread(() -> {
                        buttons[index].setText(String.valueOf(enemySymbol));
                        myTurn = true;
                        textTurnStatus.setText("[YOUR TURN]");
                    });
                }
            } catch (IOException e) {
                break;
            }
        }
    }

    public void onCellClicked(View v) {
        int id = v.getId();

        int index = -1;
        for (int i = 0; i < 9; i++)
            if (buttons[i].getId() == id)
                index = i;

        if (!myTurn) return;
        if (!buttons[index].getText().toString().isEmpty()) return;

        buttons[index].setText(String.valueOf(mySymbol));
        enviarJugada(index);

        myTurn = false;
        textTurnStatus.setText("[WAITING...]");
    }
}
