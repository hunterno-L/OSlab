// 磁盘调度：扫描算法（SCAN）
#include <algorithm>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <vector>
using namespace std;

void scan(int head, vector<int> requests) {
    // @trace:initialize
    sort(requests.begin(), requests.end());
    int total = 0, split = 0;
    while (split < (int)requests.size() && requests[split] <= head) ++split;
    for (int i = split - 1; i >= 0; --i) {
        // @trace:seek
        // 先向磁道号减小的方向移动
        total += abs(requests[i] - head);
        head = requests[i];
        cout << "磁头移动到 " << head << '\n';
    }
    // @trace:boundary
    // 到达最低请求后改变方向
    cout << "改变方向，向磁道号增大的方向移动" << '\n';
    for (int i = split; i < (int)requests.size(); ++i) {
        // @trace:seek
        // 再向磁道号增大的方向移动
        total += abs(requests[i] - head);
        head = requests[i];
        cout << "磁头移动到 " << head << '\n';
    }
    // @trace:result
    cout << fixed << setprecision(2)
         << "总移动距离 " << total << '\n'
         << "平均寻道长度 " << double(total) / requests.size() << '\n';
}

int main() {
    vector<int> requests = {95, 183, 37, 122, 14, 130, 65, 80};
    scan(53, requests);
}
