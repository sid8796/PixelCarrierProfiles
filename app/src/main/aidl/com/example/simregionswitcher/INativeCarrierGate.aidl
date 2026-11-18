package com.example.simregionswitcher;
import android.os.Bundle;
interface INativeCarrierGate {
    Bundle claim(String token);
    Bundle identity(String token);
    Bundle installApns(String token);
    void progress(String token, String stage);
}
