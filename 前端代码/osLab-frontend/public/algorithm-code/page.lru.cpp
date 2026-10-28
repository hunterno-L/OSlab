// 页面置换：最近最久未使用（LRU）
#include <iomanip>
#include <iostream>
#include <vector>
using namespace std;

void lru(vector<int> pages, int frameCount) {
    // @trace:initialize
    vector<int> frames(frameCount, -1), age(frameCount, 0);
    int faults = 0, replacements = 0;
    for (int i = 0; i < (int)pages.size(); ++i) {
        int page = pages[i];
        for (int j = 0; j < frameCount; ++j)
            if (frames[j] >= 0) ++age[j];
        int hit = -1, empty = -1;
        for (int j = 0; j < frameCount; ++j) {
            // @trace:search
            // 查找页框：命中或记录空闲页框
            if (frames[j] == page) { hit = j; break; }
            if (empty < 0 && frames[j] < 0) empty = j;
        }
        if (hit >= 0) {
            // @trace:hit
            // 命中后刷新最近使用时间
            age[hit] = 0;
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
            age[empty] = 0;
        } else {
            int victim = 0;
            // @trace:replace
            // 选择最久未使用的页面淘汰
            for (int j = 1; j < frameCount; ++j)
                if (age[j] > age[victim]) victim = j;
            frames[victim] = page;
            age[victim] = 0;
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
    lru(pages, 3);
}
