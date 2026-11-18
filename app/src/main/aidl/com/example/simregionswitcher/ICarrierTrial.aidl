package com.example.simregionswitcher;
import android.os.Bundle;
interface ICarrierTrial {
    Bundle prepare(in String requestId, in Bundle plan) = 0;
    Bundle apply(in String ticket) = 1;
    Bundle inspect(in String requestId) = 2;
    Bundle applyDirect(in String requestId, in Bundle plan) = 3;
    void destroy() = 16777114;
}

