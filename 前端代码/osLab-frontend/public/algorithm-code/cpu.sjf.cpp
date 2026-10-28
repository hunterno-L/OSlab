// 非抢占式短作业优先（SJF）调度
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

bool byArrive(const PCB& a, const PCB& b) {
    return a.arrive < b.arrive;
}

void sjf(vector<PCB> jobs) {
    // @trace:initialize
    sort(jobs.begin(), jobs.end(), byArrive);
    vector<bool> done(jobs.size(), false);
    int now = jobs[0].arrive, finished = 0;
    double turnSum = 0, weightSum = 0;
    while (finished < (int)jobs.size()) {
        int pick = -1;
        for (int i = 0; i < (int)jobs.size(); ++i) {
            if (done[i] || jobs[i].arrive > now) continue;
            // @trace:ready
            // 在已到达的进程中选运行时间最短的
            if (pick < 0 || jobs[i].work < jobs[pick].work) pick = i;
        }
        if (pick < 0) {
            // @trace:idle
            // 没有就绪进程，等待最早到达的进程
            for (int i = 0; i < (int)jobs.size(); ++i)
                if (!done[i]) { now = jobs[i].arrive; break; }
            continue;
        }
        // @trace:run
        // 选中进程开始运行，中途不被抢占
        jobs[pick].start = now;
        now += jobs[pick].work;
        // @trace:complete
        jobs[pick].finish = now;
        done[pick] = true;
        ++finished;
        int turn = jobs[pick].finish - jobs[pick].arrive;
        turnSum += turn;
        weightSum += double(turn) / jobs[pick].work;
        cout << jobs[pick].name << ": " << jobs[pick].start
             << " -> " << jobs[pick].finish << '\n';
    }
    // @trace:result
    cout << fixed << setprecision(2)
         << "平均周转时间 " << turnSum / jobs.size() << '\n'
         << "平均带权周转时间 " << weightSum / jobs.size() << '\n';
}

int main() {
    vector<PCB> jobs = {
        {"A", 1, 8}, {"B", 4, 2},
        {"C", 2, 10}, {"D", 3, 3}};
    sjf(jobs);
}
