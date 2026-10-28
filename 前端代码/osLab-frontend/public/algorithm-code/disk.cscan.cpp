// 磁盘调度：循环扫描算法（C-SCAN）
#include <algorithm>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <vector>
using namespace std;

void cscan(int head, vector<int> requests) {
    // @trace:initialize
    sort(requests.begin(), requests.end());
    int total = 0, split = 0;
    while (split < (int)requests.size() && requests[split] < head) ++split;
    for (int i = split; i < (int)requests.size(); ++i) {
        // @trace:seek
        // 先向磁道号增大的方向移动
        total += abs(requests[i] - head);
        head = requests[i];
        cout << "磁头移动到 " << head << '\n';
    }
    for (int i = 0; i < split; ++i) {
        if (i == 0) {
            // @trace:jump
            // 磁头快速返回最小请求，形成循环扫描
            total += abs(requests[0] - head);
            head = requests[0];
            cout << "磁头回到 " << head << '\n';
        } else {
            // @trace:seek
            // 返回后继续向磁道号增大的方向移动
            total += abs(requests[i] - head);
            head = requests[i];
            cout << "磁头移动到 " << head << '\n';
        }
    }
    // @trace:result
    cout << fixed << setprecision(2)
         << "总移动距离 " << total << '\n'
         << "平均寻道长度 " << double(total) / requests.size() << '\n';
}

int main() {
    vector<int> requests = {95, 183, 37, 122, 14, 130, 65, 80};
    cscan(53, requests);
}
