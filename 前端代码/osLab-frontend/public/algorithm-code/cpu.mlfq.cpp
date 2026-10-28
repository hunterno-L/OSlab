// 多级反馈队列（MLFQ）调度
#include <algorithm>
#include <iostream>
#include <string>
#include <vector>
using namespace std;

struct PCB {
    string name;
    int arrive, work;
    int remain, level;
    int start, finish;
};

bool byArrive(const PCB& a, const PCB& b) {
    return a.arrive < b.arrive;
}

void mlfq(vector<PCB> jobs, vector<int> quantum) {
    // @trace:initialize
    sort(jobs.begin(), jobs.end(), byArrive);
    for (auto& p : jobs) {
        p.remain = p.work;
        p.start = -1;
    }
    int now = jobs[0].arrive, done = 0;
    while (done < (int)jobs.size()) {
        int pick = -1;
        for (int i = 0; i < (int)jobs.size(); ++i) {
            if (jobs[i].remain == 0 || jobs[i].arrive > now) continue;
            // @trace:ready
            // 优先选择队列级别最高的进程
            if (pick < 0 || jobs[i].level < jobs[pick].level) pick = i;
        }
        if (pick < 0) {
            // @trace:idle
            // 没有就绪进程，时间推进到下一个到达点
            for (int i = 0; i < (int)jobs.size(); ++i)
                if (jobs[i].remain > 0) { now = jobs[i].arrive; break; }
            continue;
        }
        int level = jobs[pick].level;
        int slice = min(quantum[level], jobs[pick].remain);
        for (int i = 0; i < (int)jobs.size(); ++i) {
            if (jobs[i].remain == 0 || i == pick) continue;
            if (jobs[i].level < level && jobs[i].arrive > now &&
                jobs[i].arrive < now + slice) {
                // @trace:preempt
                // 更高级队列的进程到达，当前时间片被抢占
                slice = jobs[i].arrive - now;
                break;
            }
        }
        if (jobs[pick].start < 0) jobs[pick].start = now;
        // @trace:run
        // 使用当前级别的时间片运行
        now += slice;
        jobs[pick].remain -= slice;
        if (jobs[pick].remain == 0) {
            // @trace:complete
            jobs[pick].finish = now;
            ++done;
            cout << jobs[pick].name << " 完成于 " << now << '\n';
        } else if (level + 1 < (int)quantum.size()) {
            // @trace:demote
            // 未运行完，降入下一级队列
            ++jobs[pick].level;
        } else {
            // @trace:yield
            // 已在最后一级，继续轮转
            cout << jobs[pick].name << " 继续轮转" << '\n';
        }
    }
    // @trace:result
    for (const auto& p : jobs)
        cout << p.name << ": " << p.start << " -> " << p.finish << '\n';
}

int main() {
    vector<PCB> jobs = {
        {"A", 1, 13}, {"B", 0, 8},
        {"C", 1, 6}, {"D", 1, 5}};
    mlfq(jobs, {1, 2, 4});
}
