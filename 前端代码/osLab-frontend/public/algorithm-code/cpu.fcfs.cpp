// 先来先服务（FCFS）调度
#include <algorithm>
#include <iomanip>
#include <iostream>
#include <string>
#include <vector>
using namespace std;

struct PCB {
    string name;
    int arrive, work;
    int start, finish;
};

// 按到达时间从早到晚排序
bool byArrive(const PCB& a, const PCB& b) {
    return a.arrive < b.arrive;
}

void fcfs(vector<PCB> jobs) {
    // @trace:initialize
    sort(jobs.begin(), jobs.end(), byArrive);
    int now = jobs[0].arrive;
    double turnSum = 0, weightSum = 0;
    for (auto& p : jobs) {
        // @trace:idle
        // CPU 空闲时等待下一个进程到达
        if (now < p.arrive) now = p.arrive;
        // @trace:ready
        // 进程进入就绪队列，排在队首
        p.start = now;
        // @trace:run
        // 一直占用 CPU，直到运行结束
        now += p.work;
        // @trace:complete
        p.finish = now;
        int turn = p.finish - p.arrive;
        turnSum += turn;
        weightSum += double(turn) / p.work;
        cout << p.name << ": " << p.start << " -> " << p.finish << '\n';
    }
    // @trace:result
    cout << fixed << setprecision(2)
         << "平均周转时间 " << turnSum / jobs.size() << '\n'
         << "平均带权周转时间 " << weightSum / jobs.size() << '\n';
}

int main() {
    vector<PCB> jobs = {
        {"A", 1, 5}, {"B", 4, 3},
        {"C", 2, 6}, {"D", 5, 6}};
    fcfs(jobs);
}
