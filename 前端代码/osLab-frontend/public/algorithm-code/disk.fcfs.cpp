// 磁盘调度：先来先服务（FCFS）
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <vector>
using namespace std;

void fcfs(int head, vector<int> requests) {
    // @trace:initialize
    int total = 0;
    for (int track : requests) {
        // @trace:seek
        // 按请求到达顺序移动磁头
        total += abs(track - head);
        head = track;
        cout << "磁头移动到 " << track << '\n';
    }
    // @trace:result
    cout << fixed << setprecision(2)
         << "总移动距离 " << total << '\n'
         << "平均寻道长度 " << double(total) / requests.size() << '\n';
}

int main() {
    vector<int> requests = {95, 183, 37, 122, 14, 130, 65, 80};
    fcfs(100, requests);
}
