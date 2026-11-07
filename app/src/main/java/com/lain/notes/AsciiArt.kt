package com.lain.notes

object AsciiArt {

    val LOGO: String = """
 _         _     ___  _   _
| |       / \   |_ _|| \ | |
| |      / _ \   | | |  \| |
| |___  / ___ \  | | | |\  |
|_____|/_/   \_\|___||_| \_|
""".trim('\n')

    val BOOT_LINES: List<String> = listOf(
        "navi // glyphcast boot v2.0",
        "> mounting /dev/wired ........ ok",
        "> tuning signal .............. ok",
        "> no matter where you go,",
        "  everyone's connected.",
        "> present day, present time_",
    )

    val QUOTES: List<String> = listOf(
        "no matter where you go, everyone's connected.",
        "present day... present time.",
        "close the world, open the nExt.",
        "the wired is just a medium of communication.",
        "let's all love lain.",
    )

    // Characters used while a picture is being "cast" out of noise.
    const val GLYPHS: String = "01<>/\\|=+*#%&@\$?"
    const val NOISE_CHARS: String = "*#/\\<>._:;+=-01"

    // Brightness ramps, dark -> bright (all plain ASCII so alignment never breaks).
    val RAMP_NAMES: List<String> = listOf("CLASSIC", "SOFT", "WIRED", "HATCH", "DENSE")
    val RAMPS: List<String> = listOf(
        " .:-=+*#%@",
        " .,:;-~=+*#",
        " .:;+xX\$&#@",
        " .:/\\|+xX#",
        " .'`^\",:;Il!i><~+_-?][}{1)(|/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@\$",
    )

    // Original pseudo-code used for the optional "code layer" overlay.
    val CODE: String = """
#include <wired.h>
#include <navi.h>

#define PRESENT_DAY  1
#define PRESENT_TIME -PRESENT_DAY

class Node {
  public:
    int id, signal;
    Node(int i, int s) { id = i; signal = s; }
    void link(Node* n) { signal += n->signal; }
    int  alive() { return (signal > 0); }
};

class Wired {
  public:
    Node* nodes[64];
    int count, layer;
    Wired();
    void connect(Node* a, Node* b);
    void broadcast(int id);
    int  listen(int id);
    ~Wired() {}
};

void Wired::broadcast(int id) {
  for (int i = 0; i < count; i++) {
    if (nodes[i]->alive())
      nodes[i]->link(nodes[id]);
  }
}

int Wired::listen(int id) {
  if (id < 0 || id >= count)
    return -1;
  return nodes[id]->signal;
}
""".trim('\n')
}
