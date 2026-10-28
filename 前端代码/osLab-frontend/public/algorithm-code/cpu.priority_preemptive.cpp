// 抢占式优先级调度（优先级数值越大越高）
#include <algorithm>
#include <iostream>
#include <string>
#include <vector>
using namespace std;

struct PCB {
    string name;
    int arrive, work, priority;
    int remain, start, finish;
};

bool byArrive(const PCB& a, const PCB& b) {
    return a.arrive < b.arrive;
}

void priorityPreemptive(vector<PCB> jobs) {
    // @trace:initialize
    sort(jobs.begin(), jobs.end(), byArrive);
    for (auto& p : jobs) {
        p.remain = p.work;
        p.start = -1;
    }
    int now = jobs[0].arrive, finished = 0, running = -1;
    while (finished < (int)jobs.size()) {
        int pick = -1;
        for (int i = 0; i < (int)jobs.size(); ++i) {
            if (jobs[i].remain == 0 || jobs[i].arrive > now) continue;
            // @trace:ready
            // 在所有就绪进程中选出优先级最高的
            if (pick < 0 || jobs[i].priority > jobs[pick].priority) pick = i;
        }
        if (pick < 0) {
            // @trace:idle
            // 没有就绪进程，时间推进到下一个到达点
            for (int i = 0; i < (int)jobs.size(); ++i)
                if (jobs[i].remain > 0) { now = jobs[i].arrive; break; }
            continue;
        }
        // @trace:run
        // 每个时间单位检查一次是否被更高优先级抢占
        if (running != pick) {
            if (jobs[pick].start < 0) jobs[pick].start = now;
            running = pick;
        }
        --jobs[pick].remain;
        ++now;
        if (jobs[pick].remain == 0) {
            // @trace:complete
            jobs[pick].finish = now;
            ++finished;
            cout << jobs[pick].name << " 完成于 " << now << '\n';
            running = -1;
        } else {
            // @trace:preempt
            // 新到达的高优先级进程会抢占当前进程
            for (int i = 0; i < (int)jobs.size(); ++i)
                if (jobs[i].arrive == now && jobs[i].priority > jobs[pick].priority)
                    running = -1;
        }
    }
    // @trace:result
    for (const auto& p : jobs)
        cout << p.name << ": " << p.start << " -> " << p.finish << '\n';
}

int main() {
    vector<PCB> jobs = {
        {"A", 0, 6, 1}, {"B", 2, 5, 5},
        {"C", 8, 10, 3}, {"D", 10, 4, 2}};
    priorityPreemptive(jobs);
}
