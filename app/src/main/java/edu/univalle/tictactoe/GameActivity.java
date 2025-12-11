package edu.univalle.tictactoe;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class GameActivity extends AppCompatActivity {

    private TextView textTurnStatus;
    private Button[] buttons = new Button[9]; // Botones del tablero,son 9.
    private String role;
    private String serverAddress; //Dirección del Servidor

    private BluetoothSocket socket;
    private BluetoothServerSocket serverSocket;
    private OutputStream outputStream; //Para enviar
    private InputStream inputStream; // Para recibir

    // UUID estandar Bluetooth SPP
    private final UUID APP_UUID = UUID.fromString(
            "00001101-0000-1000-8000-00805F9B34FB"
    );

    private boolean myTurn = false;
    private char mySymbol;
    private char enemySymbol;
    private char[] board = new char[9]; //El tablero del tictac que usa los 9botones
    private boolean gameOver = false;
    private boolean iStartCurrentGame = false; // Aquí es quién inicia el juego
    private static final byte GAME_RESET_CODE = (byte) 255; // Código para reiniciar el juego
    private static final byte GAME_WIN_CODE = (byte) 254;   // El otro ganó
    private static final byte GAME_TIE_CODE = (byte) 253;   // Este es para el empate
    private static final byte REMATCH_REQUEST_CODE = (byte) 200; // Solicitud de revancha
    private static final byte REMATCH_ACCEPT_CODE = (byte) 201;  // Para aceptar revancha
    private static final byte REMATCH_EXIT_CODE = (byte) 250;    // Salir después de la partida

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        textTurnStatus = findViewById(R.id.textTurnStatus);

        //Aquí inicializo todos los botones
        buttons[0] = findViewById(R.id.b0);
        buttons[1] = findViewById(R.id.b1);
        buttons[2] = findViewById(R.id.b2);
        buttons[3] = findViewById(R.id.b3);
        buttons[4] = findViewById(R.id.b4);
        buttons[5] = findViewById(R.id.b5);
        buttons[6] = findViewById(R.id.b6);
        buttons[7] = findViewById(R.id.b7);
        buttons[8] = findViewById(R.id.b8);

        //Para obtener los datos del intento
        role = getIntent().getStringExtra("role");
        serverAddress = getIntent().getStringExtra("address");

        inicializarTablero();
        configurarRol();

        if (role.equals("SERVER")) {
            iniciarServidor();
        } else {
            iniciarCliente(serverAddress);
        }
    }

    private void inicializarTablero() {
        for (int i = 0; i < 9; i++) {
            board[i] = ' ';
            buttons[i].setText("");
            buttons[i].setEnabled(true);
        }
        gameOver = false;
        //Aquí limpio el tablero
    }

    private void configurarRol() {
        if (role.equals("SERVER")) {
            mySymbol = 'X';
            enemySymbol = 'O';
            myTurn = true;
            iStartCurrentGame = true;
            textTurnStatus.setText(getString(R.string.server_mode_your_turn));
        } else {
            mySymbol = 'O';
            enemySymbol = 'X';
            myTurn = false;
            iStartCurrentGame = false;
            textTurnStatus.setText(getString(R.string.client_mode_waiting));
        }
    }



    // ---------------Server----------------------

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void iniciarServidor() {
        if (!tienePermisoBtConnect()) {
            runOnUiThread(() ->
                    textTurnStatus.setText(getString(R.string.server_error_permiso))
            );
            return;
        }

        new Thread(() -> {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("TicTacToeUV", APP_UUID);

                runOnUiThread(() ->
                        textTurnStatus.setText(getString(R.string.server_waiting_client))
                );

                socket = serverSocket.accept();  // Va a bloquear hasta que el cliente se conecte
                serverSocket.close();

                abrirStreams();
                iniciarRecepcion();

                runOnUiThread(() ->
                        textTurnStatus.setText(getString(R.string.client_connected))
                );

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        textTurnStatus.setText(getString(R.string.server_error))
                );
            }
        }).start();
        // Servior inicializado


    }

    // -----------------------
    // ---------------Parte del Clientee----------------------
    // -----------------------
    private void iniciarCliente(String address) {
        if (!tienePermisoBtConnect()) {
            runOnUiThread(() ->
                    textTurnStatus.setText(getString(R.string.client_error_permiso))
            );
            return;
        }

        new Thread(() -> {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                BluetoothDevice device = adapter.getRemoteDevice(address);

                socket = device.createRfcommSocketToServiceRecord(APP_UUID);

                runOnUiThread(() ->
                        textTurnStatus.setText(getString(R.string.client_connecting))
                );

                socket.connect(); //Conectar

                abrirStreams();
                iniciarRecepcion();

                runOnUiThread(() ->
                        textTurnStatus.setText(getString(R.string.connected))
                );

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        textTurnStatus.setText(getString(R.string.connection_failed))
                );
            }
        }).start();
    }

    private boolean tienePermisoBtConnect() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    // -----------------------
    // Streams
    // -----------------------
    private void abrirStreams() throws IOException {
        outputStream = socket.getOutputStream();
        inputStream = socket.getInputStream();
    }

    // -----------------------
    // Recepción de jugadas
    // -----------------------
    private void iniciarRecepcion() {
        new Thread(() -> {
            byte[] buffer = new byte[1];

            while (true) {
                try {
                    int bytes = inputStream.read(buffer);

                    if (bytes > 0) {
                        byte received = buffer[0];
                        
                        // Verificar códigos especiales
                        if (received == GAME_RESET_CODE) {
                            runOnUiThread(() -> {
                                reiniciarJuego(iStartCurrentGame);
                                textTurnStatus.setText(getString(R.string.juego_reiniciado));
                            });
                        } else if (received == GAME_WIN_CODE) {
                            runOnUiThread(() -> {
                                gameOver = true;
                                deshabilitarBotones();
                                mostrarResultado(getString(R.string.perdiste), false);
                            });
                        } else if (received == GAME_TIE_CODE) {
                            runOnUiThread(() -> {
                                gameOver = true;
                                deshabilitarBotones();
                                mostrarResultado(getString(R.string.empate), false);
                            });
                        } else if (received == REMATCH_REQUEST_CODE) {
                            runOnUiThread(this::mostrarDialogoRematchRecibido);
                        } else if (received == REMATCH_ACCEPT_CODE) {
                            runOnUiThread(() -> {
                                // El otro jugador ya alternó quién inicia, nosotros también alternamos
                                // para mantener sincronización (si empezamos opuestos, seguimos opuestos)
                                iStartCurrentGame = !iStartCurrentGame;
                                reiniciarJuego(iStartCurrentGame);
                                textTurnStatus.setText(getString(R.string.rematch_aceptado_start));
                            });
                        } else if (received == REMATCH_EXIT_CODE) {
                            runOnUiThread(this::enviarSalidaYTerminar);
                        } else {
                            int index = received;
                            runOnUiThread(() ->
                                    procesarMovimientoRival(index)
                            );
                        }
                    }

                } catch (IOException e) {
                    runOnUiThread(() ->
                            textTurnStatus.setText(getString(R.string.disconnected))
                    );
                    break;
                }
            }
        }).start();
    }

    private void procesarMovimientoRival(int index) {
        if (gameOver) return;
        
        buttons[index].setText(String.valueOf(enemySymbol));
        board[index] = enemySymbol;
        
        // Verificar si el rival ganó o hay empate
        char winner = verificarGanador();
        if (winner != ' ') {
            gameOver = true;
            deshabilitarBotones();
            if (winner == enemySymbol) {
                mostrarResultado(getString(R.string.perdiste), true);
            } else {
                try {
                    if (outputStream != null) {
                        outputStream.write(GAME_TIE_CODE);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mostrarResultado(getString(R.string.empate), true);
            }
        } else if (esEmpate()) {
            gameOver = true;
            deshabilitarBotones();
            // Notificar al otro jugador del empate
            try {
                if (outputStream != null) {
                    outputStream.write(GAME_TIE_CODE);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            mostrarResultado(getString(R.string.empate), true);
        } else {
            myTurn = true;
            textTurnStatus.setText(getString(R.string.your_turn));
        }
    }

    // -----------------------
    // Enviar jugada
    // -----------------------
    private void enviarMovimiento(int index) {
        if (outputStream == null) {
            // Sin canal: marcar desconexión y evitar crash
            textTurnStatus.setText(getString(R.string.disconnected));
            myTurn = false;
            return;
        }
        try {
            outputStream.write(index);
        } catch (IOException e) {
            e.printStackTrace();
            textTurnStatus.setText(getString(R.string.send_error));
            myTurn = false;
        }
    }

    // -----------------------
    // Click en celda
    // -----------------------
    public void onCellClicked(View v) {
        if (!myTurn || gameOver) return;

        int index = -1;

        for (int i = 0; i < 9; i++) {
            if (buttons[i].getId() == v.getId()) {
                index = i;
            }
        }

        if (index < 0) return;
        if (board[index] != ' ') return;

        buttons[index].setText(String.valueOf(mySymbol));
        board[index] = mySymbol;

        enviarMovimiento(index);

        // Verificar si gané o hay empate
        char winner = verificarGanador();
        if (winner != ' ') {
            gameOver = true;
            deshabilitarBotones();
            if (winner == mySymbol) {
                // Yo gané, notificar al otro jugador
                try {
                    if (outputStream != null) {
                        outputStream.write(GAME_WIN_CODE);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mostrarResultado(getString(R.string.ganaste), true);
            } else {
                try {
                    if (outputStream != null) {
                        outputStream.write(GAME_TIE_CODE);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mostrarResultado(getString(R.string.empate), true);
            }
        } else if (esEmpate()) {
            gameOver = true;
            deshabilitarBotones();
            // Notificar al otro jugador del empate
            try {
                if (outputStream != null) {
                    outputStream.write(GAME_TIE_CODE);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            mostrarResultado(getString(R.string.empate), true);
        } else {
            myTurn = false;
            textTurnStatus.setText(getString(R.string.waiting));
        }
    }

    // -----------------------
    // Botón reset manual
    // -----------------------
    public void onResetClicked(View v) {
        if (outputStream != null) {
            try {
                outputStream.write(GAME_RESET_CODE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        reiniciarJuego(iStartCurrentGame);
        textTurnStatus.setText(getString(R.string.juego_reiniciado));
    }

    // -----------------------
    // Verificar ganador
    // -----------------------
    private char verificarGanador() {
        // Filas
        for (int i = 0; i < 3; i++) {
            if (board[i * 3] != ' ' && board[i * 3] == board[i * 3 + 1] && board[i * 3] == board[i * 3 + 2]) {
                return board[i * 3];
            }
        }
        
        // Columnas
        for (int i = 0; i < 3; i++) {
            if (board[i] != ' ' && board[i] == board[i + 3] && board[i] == board[i + 6]) {
                return board[i];
            }
        }
        
        // Diagonal principal
        if (board[0] != ' ' && board[0] == board[4] && board[0] == board[8]) {
            return board[0];
        }
        
        // Diagonal secundaria
        if (board[2] != ' ' && board[2] == board[4] && board[2] == board[6]) {
            return board[2];
        }
        
        return ' '; // No hay ganador
    }

    // -----------------------
    // Verificar empate
    // -----------------------
    private boolean esEmpate() {
        if (verificarGanador() != ' ') {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            if (board[i] == ' ') {
                return false;
            }
        }
        return true;
    }

    // -----------------------
    // Deshabilitar botones
    // -----------------------
    private void deshabilitarBotones() {
        for (int i = 0; i < 9; i++) {
            buttons[i].setEnabled(false);
        }
    }

    // -----------------------
    // Mostrar resultado y diálogo
    // -----------------------
    private void mostrarResultado(String resultado, boolean mostrarDialogo) {
        String mensaje;
        if (resultado.equals(getString(R.string.ganaste))) {
            mensaje = getString(R.string.mensaje_ganaste);
        } else if (resultado.equals(getString(R.string.perdiste))) {
            mensaje = getString(R.string.mensaje_perdiste);
        } else {
            mensaje = getString(R.string.mensaje_empate);
        }

        textTurnStatus.setText("[" + resultado + "]");

        if (!mostrarDialogo) return;

        new AlertDialog.Builder(this, R.style.CyberAlertDialog)
                .setTitle(getString(R.string.fin_del_juego))
                .setMessage(mensaje + "\n\n" + getString(R.string.revancha_o_salir))
                .setPositiveButton(getString(R.string.revancha), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        solicitarRevancha();
                    }
                })
                .setNegativeButton(getString(R.string.salir), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        enviarSalidaYTerminar();
                    }
                })
                .setCancelable(false)
                .show();
    }

    // -----------------------
    // Reiniciar juego
    // -----------------------
    private void reiniciarJuego(boolean startForMe) {
        inicializarTablero();
        gameOver = false;
        iStartCurrentGame = startForMe;

        if (startForMe) {
            myTurn = true;
            textTurnStatus.setText(getString(R.string.start_your_turn));
        } else {
            myTurn = false;
            textTurnStatus.setText(getString(R.string.start_rival_turn));
        }
    }

    // -----------------------
    // Revancha
    // -----------------------
    private void solicitarRevancha() {
        // Marcar que estamos esperando confirmación de revancha
        textTurnStatus.setText(getString(R.string.rematch_solicitado_esperando));
        try {
            if (outputStream != null) {
                outputStream.write(REMATCH_REQUEST_CODE);
            }
        } catch (IOException e) {
            e.printStackTrace();
            textTurnStatus.setText(getString(R.string.error_enviar_revancha));
        }
    }

    private void mostrarDialogoRematchRecibido() {
        if (gameOver) {
            new AlertDialog.Builder(this, R.style.CyberAlertDialog)
                    .setTitle(getString(R.string.revancha_titulo))
                    .setMessage(getString(R.string.revancha_mensaje))
                    .setPositiveButton(getString(R.string.aceptar), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Alternar quién inicia antes de enviar confirmación
                            iStartCurrentGame = !iStartCurrentGame;
                            try {
                                if (outputStream != null) {
                                    outputStream.write(REMATCH_ACCEPT_CODE);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                textTurnStatus.setText(getString(R.string.error_aceptar_revancha));
                                return;
                            }
                            // Reiniciar juego con el nuevo turno inicial
                            reiniciarJuego(iStartCurrentGame);
                            textTurnStatus.setText(getString(R.string.rematch_aceptado_start));
                        }
                    })
                    .setNegativeButton(getString(R.string.salir), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            enviarSalidaYTerminar();
                        }
                    })
                    .setCancelable(false)
                    .show();
        }
    }

    private void enviarSalidaYTerminar() {
        try {
            if (outputStream != null) {
                outputStream.write(REMATCH_EXIT_CODE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        finish();
    }

    // -----------------------
    // Limpieza
    // -----------------------
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // Restaurar nombre original (puede lanzar SecurityException si no hay permiso)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter.getDefaultAdapter().setName("Android");
            }
        } catch (SecurityException ignored) {
        }

        try {
            if (socket != null) socket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
