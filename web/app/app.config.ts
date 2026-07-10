export default defineAppConfig({
  ui: {
    table: {
      slots: {
        base: 'rounded-md',
        th: 'bg-muted',
        separator: 'bg-elevated',
        tr: 'border-muted/60',
      },
    },
    dashboardSidebar: {
      slots: {
        root: 'bg-elevated border-none',
      },
    },
    dashboardNavbar: {
      slots: {
        root: 'p-4! h-fit border-none',
      },
    },
    dashboardPanel: {
      slots: {
        root: 'bg-elevated',
        body: 'bg-default rounded-2xl p-5 mr-4 mb-4 flex-1',
      },
    },
  },
})
