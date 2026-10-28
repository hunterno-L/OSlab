// 邻近适应（Next Fit）分区分配
#include <iostream>
#include <string>
#include <vector>
using namespace std;

struct Partition {
    int id, size, rest;
};

struct Process {
    string name;
    int need;
};

void nextFit(vector<Partition> parts, vector<Process> jobs) {
    // @trace:initialize
    int failed = 0, last = 0;
    for (const auto& job : jobs) {
        int pick = -1;
        for (int step = 0; step < (int)parts.size(); ++step) {
            int i = (last + step) % parts.size();
            // @trace:search
            // 从上次分配位置开始，向后循环查找
            if (parts[i].rest >= job.need) { pick = i; break; }
        }
        if (pick < 0) {
            // @trace:fail
            // 没有分区装得下，分配失败
            ++failed;
            cout << job.name << " 分配失败" << '\n';
            continue;
        }
        // @trace:allocate
        // 从选中分区划出空间，下次从该分区继续
        parts[pick].rest -= job.need;
        last = pick;
        cout << job.name << " -> 分区" << parts[pick].id
             << "，剩余 " << parts[pick].rest << '\n';
    }
    // @trace:result
    cout << "分配失败 " << failed << " 个进程" << '\n';
}

int main() {
    vector<Partition> parts = {
        {0, 50, 50}, {1, 20, 20},
        {2, 70, 70}, {3, 50, 50}};
    vector<Process> jobs = {
        {"作业A", 70}, {"作业B", 90},
        {"作业C", 5}, {"作业D", 5}};
    nextFit(parts, jobs);
}
