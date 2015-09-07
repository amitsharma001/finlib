<% def s = request.getAttribute('stock') %>
<html>
    <head><title>Stock Details: ${s.symbol}</title></head>
    <body>
        <h1>Annual Returns</h1>

        <table class="table table-striped">
        <thead><tr><th>Year</th><th>Return</th></tr></thead>
        <tbody>
            <% s.annualReturns.each { k, v -> %>
                <tr><td>${v.year}</td><td>${v.theReturn}</td></tr>
            <% } %>
        </tbody>
        </table>
    </body>
</html>
