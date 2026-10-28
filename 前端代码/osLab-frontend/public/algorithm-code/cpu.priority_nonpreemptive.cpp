// 非抢占式优先级调度（优先级数值越大越高）
#include <algorithm>
#include <iostream>
#include <string>
#include <vector>
using namespace std;

struct PCB {
    string name;
    int arrive, work, priority;
    int start, finish;
};

bool byArrive(const PCB& a, const PCB& b) {
    return a.arrive < b.arrive;
}

void priorityFirst(vector<PCB> jobs) {
    // @trace:initialize
    sort(jobs.begin(), jobs.end(), byArrive);
    vector<bool> done(jobs.size(), false);
    int now = jobs[0].arrive, finished = 0;
    while (finished < (int)jobs.size()) {
        int pick = -1;
        for (int i = 0; i < (int)jobs.size(); ++i) {
            if (done[i] || jobs[i].arrive > now) continue;
            // @trace:ready
            // 在已到达进程中选优先级最高的
            if (pick < 0 || jobs[i].priority > jobs[pick].priority) pick = i;
        }
        if (pick < 0) {
            // @trace:idle
            // 没有就绪进程，等待下一个到达
            for (int i = 0; i < (int)jobs.size(); ++i)
                if (!done[i]) { now = jobs[i].arrive; break; }
            continue;
        }
        // @trace:run
        // 高优先级进程占用 CPU，中途不被抢占
        jobs[pick].start = now;
        now += jobs[pick].work;
        // @trace:complete
        jobs[pick].finish = now;
        done[pick] = true;
        ++finished;
        cout << jobs[pick].name << " P" << jobs[pick].priority
             << ": " << jobs[pick].start << " -> " << jobs[pick].finish << '\n';
    }
    // @trace:result
    cout << "全部进程执行完毕" << '\n';
}

int main() {
    vector<PCB> jobs = {
        {"A", 0, 3, 1}, {"B", 0, 1, 9},
        {"C", 0, 2, 5}};
    priorityFirst(jobs);
}
