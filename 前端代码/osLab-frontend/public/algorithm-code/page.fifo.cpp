// 页面置换：先进先出（FIFO）
#include <iomanip>
#include <iostream>
#include <vector>
using namespace std;

void fifo(vector<int> pages, int frameCount) {
    // @trace:initialize
    vector<int> frames(frameCount, -1);
    int faults = 0, replacements = 0, oldest = 0;
    for (int i = 0; i < (int)pages.size(); ++i) {
        int page = pages[i];
        int hit = -1, empty = -1;
        for (int j = 0; j < frameCount; ++j) {
            // @trace:search
            // 查找页框：判断命中或记录空闲页框
            if (frames[j] == page) { hit = j; break; }
            if (empty < 0 && frames[j] < 0) empty = j;
        }
        if (hit >= 0) {
            // @trace:hit
            // 页面已在内存，直接访问
            cout << "命中 " << page << '\n';
            continue;
        }
        // @trace:fault
        // 缺页，需要把页面装入内存
        ++faults;
        if (empty >= 0) {
            // @trace:load
            // 有空闲页框，直接装入
            frames[empty] = page;
        } else {
            // @trace:replace
            // 淘汰最早进入内存的页面
            frames[oldest] = page;
            oldest = (oldest + 1) % frameCount;
            ++replacements;
        }
        cout << "装入 " << page << " -> 页框";
        for (int p : frames) cout << ' ' << p;
        cout << '\n';
    }
    // @trace:result
    cout << fixed << setprecision(2)
         << "缺页次数 " << faults << '\n'
         << "缺页率 " << 100.0 * faults / pages.size() << "%" << '\n';
}

int main() {
    vector<int> pages = {4, 3, 2, 1, 4, 3, 5, 4, 3, 2, 1, 5};
    fifo(pages, 3);
}
