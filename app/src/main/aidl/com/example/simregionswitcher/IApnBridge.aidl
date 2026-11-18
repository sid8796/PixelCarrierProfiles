package com.example.simregionswitcher;
import android.os.Bundle;
interface IApnBridge {
    Bundle inspect(in Bundle target) = 0;
    Bundle prepare(in Bundle target, in String savedRow) = 1;
    Bundle apply(in String ticket) = 2;
    Bundle check(in String ticket) = 3;
    Bundle prepareChoice(in Bundle target, in String savedRow, long rowId) = 4;
    void destroy() = 16777114;
}
