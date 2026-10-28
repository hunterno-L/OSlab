// 页面置换：最佳置换（OPT）
#include <iomanip>
#include <iostream>
#include <vector>
using namespace std;

void opt(vector<int> pages, int frameCount) {
    // @trace:initialize
    vector<int> frames(frameCount, -1);
    int faults = 0, replacements = 0;
    for (int i = 0; i < (int)pages.size(); ++i) {
        int page = pages[i];
        int hit = -1, empty = -1;
        for (int j = 0; j < frameCount; ++j) {
            // @trace:search
            // 查找页框：命中或记录空闲页框
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
            int victim = 0, farthest = -1;
            // @trace:replace
            // 淘汰将来最长时间不会被访问的页面
            for (int j = 0; j < frameCount; ++j) {
                int next = (int)pages.size();
                for (int k = i + 1; k < (int)pages.size(); ++k)
                    if (pages[k] == frames[j]) { next = k; break; }
                if (next > farthest) { farthest = next; victim = j; }
            }
            frames[victim] = page;
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
    opt(pages, 3);
}
