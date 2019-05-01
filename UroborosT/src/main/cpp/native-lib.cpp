#include <jni.h>
#include <string>
#include <random>
#include <chrono>

class Alice {
private:
    JNICALL auto generateSeed() {
        return std::chrono::high_resolution_clock::now().time_since_epoch().count();
    }

public:

    //
};