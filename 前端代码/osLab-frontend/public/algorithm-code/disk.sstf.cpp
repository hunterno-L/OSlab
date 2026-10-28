// 磁盘调度：最短寻道时间优先（SSTF）
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <vector>
using namespace std;

void sstf(int head, vector<int> requests) {
    // @trace:initialize
    vector<bool> done(requests.size(), false);
    int total = 0;
    for (int step = 0; step < (int)requests.size(); ++step) {
        int pick = -1, nearest = 0;
        for (int i = 0; i < (int)requests.size(); ++i) {
            if (done[i]) continue;
            // @trace:seek
            // 选择离当前磁头最近的请求
            int distance = abs(requests[i] - head);
            if (pick < 0 || distance < nearest) {
                nearest = distance;
                pick = i;
            }
        }
        // @trace:seek
        // 磁头移动到最近的请求磁道
        total += nearest;
        head = requests[pick];
        done[pick] = true;
        cout << "磁头移动到 " << head << '\n';
    }
    // @trace:result
    cout << fixed << setprecision(2)
         << "总移动距离 " << total << '\n'
         << "平均寻道长度 " << double(total) / requests.size() << '\n';
}

int main() {
    vector<int> requests = {130, 100, 20, 60, 90, 140};
    sstf(80, requests);
}
