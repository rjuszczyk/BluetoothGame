package pl.rjuszczyk.bluetoothgame;

import android.bluetooth.BluetoothAdapter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.google.gson.Gson;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class RetrieveNameFromQrCodesActivity extends AppCompatActivity {

    Handler handler;
    Gson gson = new Gson();
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_retrieve_name_from_qr_codes);
        CodeScannerView scannerView= findViewById(R.id.scanner_view);

        handler = new Handler();

        final ImageView code = findViewById(R.id.code);

        final AdvertiseModel myAdvertiseModel = new AdvertiseModel();
        myAdvertiseModel.hash = System.currentTimeMillis();
        myAdvertiseModel.thisIsMe = BluetoothAdapter.getDefaultAdapter().getName();

        code.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                String qrText = gson.toJson(myAdvertiseModel);
                try {
                    code.setImageBitmap(getQRCodeImage(qrText, view.getWidth(), view.getHeight()));
                } catch (WriterException e) {
                    e.printStackTrace();
                }
            }
        });

        final CodeScanner codeScannerView = new CodeScanner(this, scannerView);
        codeScannerView.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(@NonNull final Result result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(RetrieveNameFromQrCodesActivity.this, result.getText(), Toast.LENGTH_SHORT).show();

                        String resultText = result.getText();
                        AdvertiseModel advertiseModel = gson.fromJson(resultText, AdvertiseModel.class);

                        if("12345".equals(advertiseModel.sec)) {
                            String myName = BluetoothAdapter.getDefaultAdapter().getName();
                            String otherName = advertiseModel.thisIsMe;

                            boolean iAmServer;
                            if(myName.compareTo(otherName) > 0) {
                                iAmServer = true;
                            } else if (myName.compareTo(otherName) < 0) {
                                iAmServer = false;
                            } else {
                                if(myAdvertiseModel.hash == advertiseModel.hash) {
                                    recreate();
                                    return;
                                }
                                iAmServer = myAdvertiseModel.hash > advertiseModel.hash;

                            }
                            goToNextOtherActivity(myAdvertiseModel, iAmServer, otherName);
                            return;
                        } else {
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    codeScannerView.startPreview();
                                }
                            }, 200);
                        }


                    }
                });
            }
        });
        scannerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                codeScannerView.startPreview();
            }
        });

    }

    private void goToNextOtherActivity(AdvertiseModel myAdvertiseModel, boolean iAmServer, String otherName) {
        String qrCodeTxt = gson.toJson(myAdvertiseModel);
        if(iAmServer) {
            startActivity(BluetoothServerActivity.Companion.getStartIntent(this, qrCodeTxt, otherName));
            finish();
        } else {
            startActivity(BluetoothClientActivity.Companion.getStartIntent(this, qrCodeTxt, otherName));
            finish();
        }
    }

    public static Bitmap getQRCodeImage(String text, int width, int height) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();

        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++){
            for (int y = 0; y < height; y++){
                bmp.setPixel(x, y, bitMatrix.get(x,y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bmp;
    }

    class AdvertiseModel {
        String sec = "12345";
        long hash = System.currentTimeMillis();
        String thisIsMe;
    }
}
