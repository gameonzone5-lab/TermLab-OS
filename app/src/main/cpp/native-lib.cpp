#include <jni.h>
#include <string>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/wait.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_autokaaj_termlab_MainActivity_createPTY(JNIEnv* env, jobject thiz) {
    // /dev/ptmx ওপেন করে একটি মাস্টার PTY তৈরি করা
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) return -1;

    char devname[64];
    if (ptsname_r(ptm, devname, sizeof(devname)) != 0) {
        close(ptm);
        return -1;
    }

    if (grantpt(ptm) != 0 || unlockpt(ptm) != 0) {
        close(ptm);
        return -1;
    }

    // নতুন প্রসেস তৈরি করা (Fork)
    int pid = fork();
    if (pid < 0) {
        return -1;
    }

    if (pid == 0) {
        // এটি হলো চাইল্ড প্রসেস, যেখানে শেল রান করবে
        close(ptm);
        setsid();
        int pts = open(devname, O_RDWR);
        if (pts < 0) exit(1);

        // স্ট্যান্ডার্ড ইনপুট, আউটপুট এবং এরর-কে PTY তে রিডাইরেক্ট করা
        dup2(pts, 0); 
        dup2(pts, 1); 
        dup2(pts, 2); 
        if (pts > 2) close(pts);

        // এনভায়রনমেন্ট ভ্যারিয়েবল সেট করা
        setenv("TERM", "xterm-256color", 1);
        setenv("HOME", "/data/data/com.autokaaj.termlab/files", 1);
        setenv("PATH", "/system/bin:/system/xbin", 1);

        // ডিফল্ট লিনাক্স শেল চালু করা
        execl("/system/bin/sh", "-", (char *)NULL);
        exit(1);
    }

    // মাস্টার ফাইল ডেসক্রিপ্টর কোটলিনকে ফেরত দেওয়া
    return ptm;
}
